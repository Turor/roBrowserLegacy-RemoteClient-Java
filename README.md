## Client Directory Layout

The client files should be organized in a single root data directory (configured via `client.rootpath`).

```text
client-root/
├── AI/            # Lua/Lub files for Homunculus and Mercenary AI
├── BGM/           # Background music (MP3/WAV)
├── Data/          # DATA.INI and all .grf files
├── System/        # Client-side System files (.lub, .txt, .xml)
├── cache/         # Generated cache files
├── logs/          # Application logs and missing-files.log
└── resources/     # Custom resources and path-mapping.json
```

- **AI**: Accessed via `AI/...`
- **BGM**: Accessed via `BGM/...`
- **Data**: GRFs and DATA.INI must be in this folder.
- **System**: Accessed via `System/...`
- **logs**: Application logs and error reports. Configurable via `client.logpath`.
- **resources**: Fallback for any other file request.

## Configuration

The application is configured in `application.properties`:

### Client Settings
- `client.rootpath`: Root directory for client files.
- `client.resourcespath`: Relative path to custom resources directory (default: `resources`).
- `client.logpath`: Relative path to logs directory (default: `logs`).
- `client.dataininame`: Name of the `DATA.INI` file (default: `Data.ini`).
- `client.usepathmappings`: Enable path mappings (default: `true`).
- `client.autoextract`: Automatically extract missing files from GRFs (default: `true`).
- `client.enablesearch`: Enable file search functionality (default: `true`).
- `client.public-url`: The public URL of the client (default: `http://localhost:3338`).

### Cache Settings
- `client.cache.max-files`: Maximum number of files to keep in cache (default: `5000`).
- `client.cache.max-memory-mb`: Maximum memory in MB to use for cache (default: `1024`).
- `client.cache.warmup.enabled`: Enable cache warm-up on startup (default: `true`).
- `client.cache.warmup.limit`: Maximum number of files to pre-load during warm-up (default: `500`).

### WebSocket Proxy Settings
- `wsproxy.enabled`: Enable the WebSocket proxy (default: `true`, env: `ENABLE_WSPROXY`).
- `wsproxy.allowed-targets`: Comma-separated list of allowed target addresses (env: `WS_ALLOWED_TARGETS`).

### Server & System Settings
- `micronaut.server.port`: The port the application listens on (default: `3338`).
- `client.tasks.validate-grfs`: Validate all GRFs on startup (default: `true`).
- `client.tasks.mapping-output`: Filename for path mapping output (env: `MAPPING_OUTPUT`).
- `LOG_LEVEL`: Root logging level (default: `DEBUG`).

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE.md).

## Micronaut 4.10.14 Documentation

- [User Guide](https://docs.micronaut.io/4.10.14/guide/index.html)
- [API Reference](https://docs.micronaut.io/4.10.14/api/index.html)
- [Configuration Reference](https://docs.micronaut.io/4.10.14/guide/configurationreference.html)
- [Micronaut Guides](https://guides.micronaut.io/index.html)

---

- [Micronaut Gradle Plugin documentation](https://micronaut-projects.github.io/micronaut-gradle-plugin/latest/)
- [GraalVM Gradle Plugin documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
- [Shadow Gradle Plugin](https://gradleup.com/shadow/)

## Feature micronaut-aot documentation

- [Micronaut AOT documentation](https://micronaut-projects.github.io/micronaut-aot/latest/guide/)

## Feature serialization-jackson documentation

- [Micronaut Serialization Jackson Core documentation](https://micronaut-projects.github.io/micronaut-serialization/latest/guide/)

## Feature management documentation

- [Micronaut Management documentation](https://docs.micronaut.io/latest/guide/index.html#management)

## Feature cache-caffeine documentation

- [Micronaut Caffeine Cache documentation](https://micronaut-projects.github.io/micronaut-cache/latest/guide/index.html)


- [https://github.com/ben-manes/caffeine](https://github.com/ben-manes/caffeine)


