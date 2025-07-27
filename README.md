# Keycloak SAML extended group mapper

This *Keycloak* mapper was intended to solve the problem of passing user's groups from LDAP to *Nextcloud* via *Keycloak* SAML provider.

So when Keycloak fetches groups a user belongs to, one needs some mapping from LDAP groups to SAML attributes. As of **Keycloak 25.0.1** such mapper was *Group list*. This extended mapper is based on [GroupMembershipMapper.java](https://github.com/keycloak/keycloak/tree/25.0.1/services/src/main/java/org/keycloak/protocol/saml/mappers/GroupMembershipMapper.java) with minimum modifications *as possible* in order to solve two tasks:

* filter user's groups that match precific pattern;

* optionally strip this pattern from the matched group name.

## Build

Perhaps the easiest way to build the sources into a JAR file (*.jar) is to use `registry.access.redhat.com/ubi9/openjdk-21:latest` as a build container. However, it seems to lack of *Git*, so one can create a build environment based on that image:

```Dockerfile
FROM registry.access.redhat.com/ubi9/openjdk-21:latest
 
USER root

# Install Git
RUN microdnf install -y --nodocs --setopt=install_weak_deps=0 git
 
USER default
```

Build container (assuming the `Dockerfile` is in the current working directory), run it and build the plugin:

```shell
docker build --network=host -t docker.io/valv/openjdk-21-git:latest
docker run --rm -ti -v $(pwd)/build:/home/default/build:Z valv/openjdk-21-git bash

cd /home/default/build
git clone https://github.com/ValV/keycloak-saml-group-mapper.git

cd keycloak-saml-group-mapper
mvn clean package
```

Ok, if the build process succeeded, the result is `build/keycloak-saml-group-mapper/target/keycloak-saml-group-mapper-1.0.0.jar`, and must be deployed into *Keycloak*'s `/opt/keycloak/providers`.

Assume *Keycloak* is deployed as a containerized application via `docker-compose.yaml` and container name is *keycloak*.

1. Backup existing *Keycloak* providers (optionally):

```bash
docker cp keycloak:/opt/keycloak/providers ./
```

2. Copy resulting JAR file into the providers directory:

```bash
cp build/keycloak-saml-group-mapper/target/keycloak-saml-group-mappe
r-1.0.0.jar providers/
```

3. Mount the providers directory in `docker-compose.yaml`:

```yaml
services:
...
  keycloak:
    container_name: keycloak
    image: quay.io/keycloak/keycloak:${KEYCLOAK_IMAGE_TAG}
...
    volumes:
      - ./providers:/opt/keycloak/providers:Z
...
```
4. Restart *Keycloak*:

```bash
docker compose up -d
```

## Configure

Whenever it's necessary to add a mapper in `Client scopes` -> *[scope name]* -> `Mappers` -> `Add mapper` -> `By configuration`, a new mapper named *Group list extended* will be available.

It's almost like vanilla *Group list* with two new controls added:

* `Group pattern match` - text field for a string pattern to filter groups against;

* `Group pattern strip` - radio switch that will remove matched pattern for every found group name if checked.
