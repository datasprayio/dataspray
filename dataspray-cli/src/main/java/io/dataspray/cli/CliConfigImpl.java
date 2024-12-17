/*
 * Copyright 2024 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.cli;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import io.dataspray.core.StreamRuntime.Organization;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class CliConfigImpl implements CliConfig {
    public static final String PROFILE_ENV_NAME = "DST_PROFILE";
    private static final String CONFIG_FILE = System.getProperty("user.home", "~") + File.separator + ".dst";
    private static final String PROPERTY_DEFAULT_PROFILE = "default";
    private static final String PROPERTY_ORGANIZATION_NAME = "organization";
    private static final String PROPERTY_API_KEY = "api_key";
    private static final String PROPERTY_ENDPOINT = "endpoint";

    private Optional<INIConfiguration> iniCacheOpt = Optional.empty();

    public ConfigState getConfigState() {
        return new ConfigState(
                CONFIG_FILE,
                getDefaultProfileName(),
                getRootConfig().getSections().stream()
                        .collect(ImmutableMap.toImmutableMap(
                                profileName -> profileName,
                                profileName -> getProfile(profileName).orElseThrow())));
    }

    @Override
    public Organization getProfile(Optional<String> profileNameOpt) {

        // Find from parameter
        if (profileNameOpt.isPresent()) {
            return getProfile(profileNameOpt.get())
                    .orElseThrow(() -> new RuntimeException("No profile found with name " + profileNameOpt.get()));
        }

        // Find from environment variable
        Optional<String> organizationFromEnvOpt = Optional.ofNullable(Strings.emptyToNull(System.getenv(PROFILE_ENV_NAME)));
        if (organizationFromEnvOpt.isPresent()) {
            return getProfile(organizationFromEnvOpt.get())
                    .orElseThrow(() -> new RuntimeException("No profile found with name " + organizationFromEnvOpt.get() + " defined in environment variable " + PROFILE_ENV_NAME));
        }

        // Find from default config
        Optional<String> organizationFromConfigDefault = Optional.ofNullable(Strings.emptyToNull(getRootConfig().getString(PROPERTY_DEFAULT_PROFILE)));
        if (organizationFromConfigDefault.isPresent()) {
            return getProfile(organizationFromConfigDefault.get())
                    .orElseThrow(() -> new RuntimeException("No API key found for organization " + profileNameOpt.get() + " defined as default in config"));
        }

        throw new RuntimeException("No API key found. Please login first.");
    }

    private Optional<Organization> getProfile(String profileName) {
        SubnodeConfiguration config = getProfileConfig(profileName);

        Optional<String> organizationNameOpt = Optional.ofNullable(Strings.emptyToNull(config.getString(PROPERTY_ORGANIZATION_NAME)));
        if (organizationNameOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> apiKeyOpt = Optional.ofNullable(Strings.emptyToNull(config.getString(PROPERTY_API_KEY)));
        if (apiKeyOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> endpointOpt = Optional.ofNullable(Strings.emptyToNull(config.getString(PROPERTY_ENDPOINT)));

        return Optional.of(new Organization(
                organizationNameOpt.get(),
                apiKeyOpt.get(),
                endpointOpt));
    }

    @Override
    public void setProfile(String profileName, Organization organization) {
        SubnodeConfiguration config = getProfileConfig(profileName);
        config.setProperty(PROPERTY_ORGANIZATION_NAME, organization.getName());
        config.setProperty(PROPERTY_API_KEY, organization.getApiKey());
        organization.getEndpoint().ifPresentOrElse(
                endpoint -> config.setProperty(PROPERTY_ENDPOINT, endpoint),
                () -> config.clearProperty(PROPERTY_ENDPOINT));
        save();
        log.info("Saved your API key under {}", CONFIG_FILE);
    }

    @Override
    public boolean hasDefaultProfileName() {
        return getDefaultProfileName().isPresent();
    }

    private Optional<String> getDefaultProfileName() {
        return Optional.ofNullable(Strings.emptyToNull(getRootConfig().getString(PROPERTY_DEFAULT_PROFILE)));
    }

    @Override
    public void setDefaultOrganization(String organizationName) {
        getRootConfig().setProperty(PROPERTY_DEFAULT_PROFILE, organizationName);
        save();
        log.info("Saved organization {} as default", organizationName);
    }

    private INIConfiguration readConfig() {
        INIConfiguration ini = new INIConfiguration();
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            ini.read(reader);
        } catch (FileNotFoundException ex) {
            log.debug("Config file not present " + CONFIG_FILE);
        } catch (ConfigurationException ex) {
            throw new RuntimeException("Failed to parse config file " + CONFIG_FILE, ex);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load config file " + CONFIG_FILE, ex);
        }
        return ini;
    }

    private INIConfiguration getRootConfig() {
        if (!iniCacheOpt.isPresent()) {
            synchronized (this) {
                if (!iniCacheOpt.isPresent()) {
                    iniCacheOpt = Optional.of(readConfig());
                }
            }
        }
        return iniCacheOpt.get();
    }

    private SubnodeConfiguration getProfileConfig(String profileName) {
        return getRootConfig().getSection(profileName);
    }

    private void save() {
        INIConfiguration ini = iniCacheOpt.orElse(readConfig());

        try (FileWriter writer = new FileWriter(CONFIG_FILE, false)) {
            ini.write(writer);
        } catch (IOException | ConfigurationException ex) {
            throw new RuntimeException("Failed to write changes to config file " + CONFIG_FILE, ex);
        }

        iniCacheOpt = Optional.of(ini);
    }
}
