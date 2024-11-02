// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.runner;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/**
 * A state manager that allows for easy and efficient access to a particular item in a DynamoDB table.
 * <p>
 * This class is thread-safe, but is not safe across tasks as it delays writes and reads with weak consistency.
 * Updates are grouped together and flushed only when necessary.
 */
public interface StateManager extends AutoCloseable {

    /**
     * Get the key of the item this state manager is managing.
     */
    String[] getKey();


    /**
     * Update the TTL to reset the expiration time.
     */
    void touch();


    <T> Optional<T> getJson(String key, Class<T> type);

    <T> void setJson(String key, T item);


    String getString(String key);

    void setString(String key, String value);


    boolean getBoolean(String key);

    void setBoolean(String key, boolean value);


    BigDecimal getNumber(String key);

    void setNumber(String key, Number number);

    void addToNumber(String key, Number increment);


    Set<String> getStringSet(String key);

    void setStringSet(String key, Set<String> set);

    void addToStringSet(String key, String... values);

    void deleteFromStringSet(String key, String... values);


    void delete(String key);


    void flush();
}
