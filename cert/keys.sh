#!/usr/bin/env bash

## Llavero Servidor
keytool -genkeypair -alias serverKeyPair -keyalg RSA -keysize 2048 -validity 365 -storetype PKCS12 -keystore server_keystore.p12 -storepass password

## Certificado del servidor
keytool -exportcert -alias serverKeyPair -storetype PKCS12 -keystore server_keystore.p12 -file server_certificate.cer -rfc -storepass password
