package io.dataspray.core.cli;

import com.google.common.base.Strings;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.configuration2.AbstractConfiguration;
import org.apache.commons.configuration2.INIConfiguration;
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
    private static final String PROPERTY_DATASPRAY_API_KEY = "api_key";

    private Optional<INIConfiguration> iniCacheOpt = Optional.empty();

    @Override
    public String getDataSprayApiKey() {
        return Optional.ofNullable(Strings.emptyToNull(getConfig().getString(PROPERTY_DATASPRAY_API_KEY)))
                .orElseThrow(() -> new RuntimeException("Need to setup your API key first"));
    }

    @Override
    public void setDataSprayApiKey(String apiKey) {
        writeProperty(PROPERTY_DATASPRAY_API_KEY, apiKey);
        log.info("Saved your API key to {}", CONFIG_FILE);
    }

    private Optional<String> getProfileName() {
        return Optional.ofNullable(Strings.emptyToNull(System.getenv(PROFILE_ENV_NAME)));
    }

    private AbstractConfiguration getProfileConfig(INIConfiguration ini) {
        return getProfileName()
                .map(name -> (AbstractConfiguration) ini.getSection(name))
                .orElse(ini);
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

    private AbstractConfiguration getConfig() {
        if (!iniCacheOpt.isPresent()) {
            synchronized (this) {
                if (!iniCacheOpt.isPresent()) {
                    iniCacheOpt = Optional.of(readConfig());
                }
            }
        }
        return getProfileConfig(iniCacheOpt.get());
    }

    private void writeProperty(String key, Object value) {
        synchronized (this) {
            INIConfiguration ini = iniCacheOpt.orElse(readConfig());

            getProfileConfig(ini).setProperty(key, value);

            try (FileWriter writer = new FileWriter(CONFIG_FILE, false)) {
                ini.write(writer);
            } catch (IOException | ConfigurationException ex) {
                throw new RuntimeException("Failed to write property " + key + " to config file " + CONFIG_FILE, ex);
            }

            iniCacheOpt = Optional.of(ini);
        }
    }
}
