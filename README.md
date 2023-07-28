<p align="center">
  <a href="https://dataspray.io/" rel="noopener" target="_blank">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="./dataspray-resources/src/main/resources/logo-and-title-dark.svg">
      <img width="450" src="./dataspray-resources/src/main/resources/logo-and-title-light.svg" alt="DataSpray">
    </picture>
  </a>
</p>

<div align="center">
  <a href="https://github.com/datasprayio/dataspray/actions?query=workflow%3A%22CI%22">
    <img alt="Build Status" src="https://img.shields.io/github/actions/workflow/status/datasprayio/dataspray/test.yml?style=for-the-badge">
  </a>
  <a href="https://github.com/datasprayio/dataspray/releases">
    <img alt="GitHub release" src="https://img.shields.io/github/v/release/datasprayio/dataspray?include_prereleases&sort=semver&style=for-the-badge">
  </a>
  <a href="https://github.com/datasprayio/dataspray/blob/master/LICENSE">
    <img alt="License" src="https://img.shields.io/github/license/datasprayio/dataspray?style=for-the-badge">
  </a>
</div>
<br />
<br />
<h3 align="center">Work in progress...</h3>

## Self-host

### From released CloudFormation template

⚠️ Not yet available...

### From repository

Ensure you installed all dependencies and run the following command:

```bash
mvn clean deploy -DdnsDomain=example.com [-DdnsParentZoneId=Z104162015L8HFMCRVJ9Y]
```

_Note: The Certificate creation step will wait and appear stuck until domain is validated via DNS. See the "Setup DNS"
section below._

### Configuration

<details>
  <summary>Setup DNS</summary>

The deployment will create a Route53 hosted zone at `dataspray.<dnsDomain>`. You can choose to use a different subdomain
using the `-DdnsSubdomain=dataspray` property.

You will need to create a NS record in your parent domain zone pointing to the subdomain zone:

1. Automatically by providing the parameter `-DdnsParentZoneId=Z104162015L8HFMCRVJ9Y` to create an NS record
   automatically that will delegate your parent zone to the created subdomain zone.
2. Manually if you're not using Route53 or wish to create the record yourself. If using a subdomain (e.g.
   dataspray.example.com), create a NS record in your top-level domain's hosted zone
   delegating the subdomain to the created hosted zone's name servers.

</details>

<details>
  <summary>Setup Email</summary>

If you wish to receive emails sent by DataSpray, you will need to verify your domain in SES and provide the
parameter `-DsesEmail=support@example.com`.

Without it, Cognito will be able to send a limited number of emails and DataSpray will not be able to send any email
notifications.
</details>

<details>
  <summary>Teardown</summary>

Resources part of the deployment can be removed by shutting down the stacks in CloudFormation in your AWS Console.

Resources created as part of using the service such as Lambda functions for processing data will have to be removed
manually at this time.
</details>

## Env Setup

```bash
brew install docker scour imagemagick

# Setup Java, Maven using SDKMAN
brew tap sdkman/tap
brew install sdkman-cli
sdk env install

# Setup NodeJS using NVM
brew install nvm
nvm install
```
