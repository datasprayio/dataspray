// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
import {isCsr} from "./isoUtil";

var envCache: Environment | undefined = undefined;

export enum Environment {
    LOCAL = 'LOCAL',
    STAGING = 'STAGING',
    PRODUCTION = 'PRODUCTION',
    SELF_HOST = 'SELF_HOST',
}

export function detectEnv(): Environment {
    if (envCache === undefined) {
        if (isCsr()) {
            if (window.location.hostname === 'dashboard.dataspray.io') {
                envCache = Environment.PRODUCTION;
            } else if (window.location.hostname === 'dashboard.staging.dataspray.io') {
                envCache = Environment.STAGING;
            } else if (window.location.hostname === 'localhost') {
                const paramsEnv = new URL(window.location.href).searchParams.get('env');
                if (!!paramsEnv && Object.values(Environment).includes(paramsEnv as any)) {
                    envCache = paramsEnv as Environment;
                } else {
                    envCache = Environment.LOCAL;
                }
            } else {
                envCache = Environment.SELF_HOST;
            }
        } else {
            if (process.env.NODE_ENV === 'production') {
                envCache = Environment.PRODUCTION
            } else {
                envCache = Environment.LOCAL
            }
        }
    }
    return envCache;
}

export function isProd(): boolean {
    return detectEnv() === Environment.PRODUCTION
        || detectEnv() === Environment.SELF_HOST;
}
