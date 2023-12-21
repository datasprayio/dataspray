/*
 * Copyright 2023 Matus Faro
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

package io.dataspray.core.cli;

import com.google.common.base.Strings;
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
    public static final String ORGANIZATION_ENV_NAME = "DST_ORG";
    private static final String CONFIG_FILE = System.getProperty("user.home", "~") + File.separator + ".dst";
    private static final String PROPERTY_DEFAULT_ORGANIZATION = "default";
    private static final String PROPERTY_API_KEY = "api_key";
    private static final String PROPERTY_ENDPOINT = "endpoint";

    private Optional<INIConfiguration> iniCacheOpt = Optional.empty();

    @Override
    public ConfigState getConfigState() {
        // TODO
    }

    @Override
    public Organization getOrganization(Optional<String> organizationFromParameterOpt) {

        // Find api key from organization defined in parameter
        if (organizationFromParameterOpt.isPresent()) {
            return getOrganization(organizationFromParameterOpt.get())
                    .orElseThrow(() -> new RuntimeException("No API key found for organization " + organizationFromParameterOpt.get()));
        }

        // Find api key from organization defined in environment variable
        Optional<String> organizationFromEnvOpt = Optional.ofNullable(Strings.emptyToNull(System.getenv(ORGANIZATION_ENV_NAME)));
        if (organizationFromEnvOpt.isPresent()) {
            return getOrganization(organizationFromEnvOpt.get())
                    .orElseThrow(() -> new RuntimeException("No API key found for organization " + organizationFromParameterOpt.get() + " defined in environment variable " + ORGANIZATION_ENV_NAME));
        }

        // Find api key from organization defined as default in config
        Optional<String> organizationFromConfigDefault = Optional.ofNullable(Strings.emptyToNull(getRootConfig().getString(PROPERTY_DEFAULT_ORGANIZATION)));
        if (organizationFromConfigDefault.isPresent()) {
            return getOrganization(organizationFromConfigDefault.get())
                    .orElseThrow(() -> new RuntimeException("No API key found for organization " + organizationFromParameterOpt.get() + " defined as default in config"));
        }

        throw new RuntimeException("No API key found. Please login first.");
    }

    /**
     * This has a limitation at the moment. You cannot have two organizations with the same name across two different
     * endpoints.
     */
    private Optional<Organization> getOrganization(String organizationName) {
        SubnodeConfiguration config = getOrganizationConfig(organizationName);

        Optional<String> apiKeyOpt = Optional.ofNullable(Strings.emptyToNull(config.getString(PROPERTY_API_KEY)));
        if (apiKeyOpt.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> endpointOpt = Optional.ofNullable(Strings.emptyToNull(config.getString(PROPERTY_ENDPOINT)));

        return Optional.of(new Organization(
                organizationName,
                apiKeyOpt.get(),
                endpointOpt));
    }

    @Override
    public void setOrganization(String organizationName, String apiKey) {
        getOrganizationConfig(organizationName).setProperty(PROPERTY_API_KEY, apiKey);
        save();
        log.info("Saved your API key to {}", CONFIG_FILE);
    }

    @Override
    public Optional<String> getDefaultOrganization() {
        return Optional.ofNullable(Strings.emptyToNull(getRootConfig().getString(PROPERTY_DEFAULT_ORGANIZATION)));
    }

    @Override
    public void setDefaultOrganization(String organizationName) {
        getRootConfig().setProperty(PROPERTY_DEFAULT_ORGANIZATION, organizationName);
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

    private SubnodeConfiguration getOrganizationConfig(String organizationName) {
        return getRootConfig().getSection(organizationName);
    }

    private void save() {
        INIConfiguration ini = iniCacheOpt.orElse(readConfig());

        try (FileWriter writer = new FileWriter(CONFIG_FILE, false)) {
            ini.write(writer);
        } catch (IOException | ConfigurationException ex) {
            throw new RuntimeException("Failed to write property " + key + " to config file " + CONFIG_FILE, ex);
        }

        iniCacheOpt = Optional.of(ini);
    }
}
