# Systemd Deployment Guide for RemoteClient

This guide explains how to run the `client` and `wsproxy` components as systemd services on a Linux system.

## Prerequisites (Debian/Ubuntu)

Before building, ensure you have Java 21 installed. On a fresh Debian system, you can install it using the following commands:

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk-headless
```

Verify the installation:

```bash
java -version
```

## 1. Build the Applications

First, build the fat JARs for both subprojects:

```bash
chmod +x gradlew
./gradlew shadowJar
```

This will produce the following JAR files:
- `client/build/libs/client-0.1-all.jar`
- `wsproxy/build/libs/wsproxy-0.1-all.jar`

## 2. Prepare the Deployment Directory

It is recommended to use `/opt/remoteclient` as the deployment directory.

```bash
sudo mkdir -p /opt/remoteclient/bin
sudo mkdir -p /opt/remoteclient/config
sudo mkdir -p /opt/remoteclient/logs
sudo mkdir -p /opt/remoteclient/resources
```

Copy the JARs, the configuration file, and the web client:

```bash
sudo cp client/build/libs/client-0.1-all.jar /opt/remoteclient/client.jar
sudo cp wsproxy/build/libs/wsproxy-0.1-all.jar /opt/remoteclient/wsproxy.jar
sudo cp application.properties.example /opt/remoteclient/config/application.properties
# Copy your web client (index.html and any other static files)
sudo cp index.html /opt/remoteclient/
```

### Ragnarok Online Data

Ensure you have your `DATA.INI` and GRF files in `/opt/remoteclient/resources`.

The application will automatically create the following directories in `/opt/remoteclient` if they don't exist:
- `BGM/`
- `Data/`
- `AI/`
- `System/`

You should populate these with your client files (e.g., Lua files in `System/`, textures in `Data/`) as needed.

## 3. Create a System User

Run the services under a dedicated non-root user:

```bash
sudo useradd -r -s /sbin/nologin remoteclient
sudo chown -R remoteclient:remoteclient /opt/remoteclient
```

## 4. Install Systemd Units

Create the following unit files in `/etc/systemd/system/`.

### `/etc/systemd/system/remote-client.service`
```ini
[Unit]
Description=RemoteClient Web Server
After=network.target

[Service]
Type=simple
User=remoteclient
Group=remoteclient
WorkingDirectory=/opt/remoteclient
Environment="MICRONAUT_APPLICATION_NAME=client"
ExecStart=/usr/bin/java \
  -Xms256m \
  -Xmx1024m \
  -Dmicronaut.server.host=0.0.0.0 \
  -Dmicronaut.config.files=/opt/remoteclient/config/application.properties \
  -Dmicronaut.server.port=3338 \
  -jar /opt/remoteclient/bin/client.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### `/etc/systemd/system/remote-proxy.service`
```ini
[Unit]
Description=RemoteClient WebSocket Proxy
After=network.target

[Service]
Type=simple
User=remoteclient
Group=remoteclient
WorkingDirectory=/opt/remoteclient
Environment="MICRONAUT_APPLICATION_NAME=wsproxy"
ExecStart=/usr/bin/java \
  -Xms256m \
  -Xmx1024m \
  -Dmicronaut.server.host=0.0.0.0 \
  -Dmicronaut.config.files=/opt/remoteclient/config/application.properties \
  -Dmicronaut.server.port=5999 \
  -jar /opt/remoteclient/bin/wsproxy.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

## 5. Enable and Start Services

```bash
sudo systemctl daemon-reload
sudo systemctl enable remote-client.service
sudo systemctl enable remote-proxy.service
sudo systemctl start remote-client.service
sudo systemctl start remote-proxy.service
```

## 6. Management

- **Check status**: `systemctl status remote-client`
- **View logs**: `journalctl -u remote-client -f` or `tail -f /opt/remoteclient/logs/client.log`
- **Stop**: `sudo systemctl stop remote-client`
- **Restart**: `sudo systemctl restart remote-client`

## Configuration Notes

- **Logging**: By default, logs are sent to journald. You can view them with `journalctl`. If you prefer file-based logging via systemd, uncomment the `StandardOutput` and `StandardError` lines in the unit files. Note that the application also performs its own logging to `${client.logpath}` as configured in `application.properties`.
- **Working Directory**: The `remote-client.service` uses `/opt/remoteclient` as the working directory to ensure that `index.html` (in the working directory) and RO data (in the `resources/` sub-directory) are correctly located. We also set `-Dclient.rootpath=/opt/remoteclient` to ensure the application knows its base directory.
- **Environment Variables**: You can override any property using environment variables, e.g., `Environment="WSPROXY_ENABLED=true"`.
- **Memory**: You can add JVM options to `ExecStart`, e.g., `ExecStart=/usr/bin/java -Xmx1024m -jar ...`.
