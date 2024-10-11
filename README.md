# SMPP Client Module

The `smpp-client-module` is a crucial component in the SMSC (Short Message Service Center) environment. This module is responsible for establishing and managing SMPP (Short Message Peer-to-Peer) connections with external gateways for message submissions. It handles retries for undelivered messages and integrates with Redis to manage message queues efficiently. Additionally, it supports WebSocket communication for real-time interaction and JMX monitoring for performance tracking.

## Key Features

- **SMPP Client Management**: Establishes and manages SMPP connections to external gateways.
- **Redis Integration**: Handles message queues and retry mechanisms using Redis.
- **WebSocket Support**: Enables real-time communication and monitoring.
- **Connection and Retry Handling**: Manages error codes, routing rules, and retry attempts for failed message submissions.
- **Thread Pool Management**: Configurable thread pool for optimized connection handling.
- **JMX Monitoring**: Provides monitoring capabilities via JMX.

## Key Configurable Variables

### JVM Settings
- **`JVM_XMS`**: Minimum heap size for the JVM. Default: `512 MB`.
- **`JVM_XMX`**: Maximum heap size for the JVM. Default: `1024 MB`.

### Application Settings
- **`SERVER_PORT`**: Port on which the client module listens. Default: `7119`.
- **`APPLICATION_NAME`**: Name of the SMPP client module instance. Default: `smpp-client-module`.

### Redis Cluster Configuration
- **`CLUSTER_NODES`**: Redis cluster nodes managing message queues. Default: `localhost:7000,...,localhost:7009`.

### Thread Pool Settings
- **`THREAD_POOL_MAX_TOTAL`**: Maximum number of threads. Default: `60`.
- **`THREAD_POOL_MAX_IDLE`**: Maximum idle threads. Default: `50`.
- **`THREAD_POOL_MIN_IDLE`**: Minimum idle threads. Default: `10`.
- **`THREAD_POOL_BLOCK_WHEN_EXHAUSTED`**: Blocks if thread pool is exhausted. Default: `true`.

### SMPP Connection Configuration
- **`KEY_GATEWAYS`**: Redis key for storing gateway information.
- **`KEY_ERROR_CODE_MAPPING`**: Redis key for service provider error codes.
- **`KEY_ROUTING_RULES`**: Redis key for routing rules.
- **`CONNECTION_PROCESSOR_DEGREE`**: Number of processors for SMPP connections. Default: `150`.
- **`CONNECTION_QUEUE_CAPACITY`**: Queue capacity for connection processing. Default: `50000`.
- **`CONNECTION_TRANSACTION_TIMER`**: Transaction timeout in milliseconds. Default: `5000`.
- **`CONNECTION_TIME_RETRY`**: Retry interval for connections in milliseconds. Default: `5000`.

### Message Submission and Retry
- **`SUBMIT_PRE_DELIVER`**: Redis list for messages awaiting delivery. Default: `preDeliver`.
- **`SUBMIT_PRE_MESSAGE`**: Redis list for pre-processed messages. Default: `preMessage`.
- **`SUBMIT_SM_RESULTS`**: Redis list for storing submission results. Default: `submit_sm_result`.
- **`RETRY_MESSAGES_QUEUE`**: Queue for retrying undelivered messages. Default: `sms_retry`.

### Worker Settings
- **`WORKS_PER_GATEWAY`**: Workers per SMPP gateway. Default: `10`.
- **`WORK_FOR_WORKER`**: Messages processed per worker. Default: `1000`.
- **`GATEWAYS_WORK_EXECUTE_EVERY`**: Interval for gateway worker execution (ms). Default: `1000`.

### WebSocket Configuration
- **`WEBSOCKET_SERVER_ENABLED`**: Enables WebSocket communication. Default: `true`.
- **`WEBSOCKET_SERVER_HOST`**: WebSocket server host. Default: `{WEBSOCKET_SERVER_HOST}`.
- **`WEBSOCKET_SERVER_PORT`**: WebSocket server port. Default: `9000`.
- **`WEBSOCKET_SERVER_PATH`**: WebSocket server path. Default: `/ws`.
- **`WEBSOCKET_SERVER_RETRY_INTERVAL`**: Retry interval for WebSocket connections (seconds). Default: `10`.
- **`WEBSOCKET_HEADER_NAME`**: WebSocket authorization header. Default: `Authorization`.
- **`WEBSOCKET_HEADER_VALUE`**: WebSocket authorization token.

### Virtual Threads
- **`THREADS_VIRTUAL_ENABLED`**: Enables virtual threads for concurrency. Default: `true`.

### JMX Monitoring
- **`ENABLE_JMX`**: Enables JMX for monitoring. Default: `true`.
- **`IP_JMX`**: JMX service IP. Default: `127.0.0.1`.
- **`JMX_PORT`**: JMX service port. Default: `9013`.

## Docker Compose Example

```yaml
services:
  smpp-client-module:
    image: paic/smpp-client-module:latest
    ulimits:
      nofile:
        soft: 1000000
        hard: 1000000
    environment:
      JVM_XMS: "-Xms512m"
      JVM_XMX: "-Xmx1024m"
      SERVER_PORT: 7119
      APPLICATION_NAME: "smpp-client-module"
      # Redis Cluster
      CLUSTER_NODES: "localhost:7000,localhost:7001,localhost:7002,localhost:7003,localhost:7004,localhost:7005,localhost:7006,localhost:7007,localhost:7008,localhost:7009"
      THREAD_POOL_MAX_TOTAL: 60
      THREAD_POOL_MAX_IDLE: 50
      THREAD_POOL_MIN_IDLE: 10
      THREAD_POOL_BLOCK_WHEN_EXHAUSTED: true
      # SMPP Connection Configuration
      KEY_GATEWAYS: "gateways"
      KEY_ERROR_CODE_MAPPING: "service_providers"
      KEY_ROUTING_RULES: "error_code_mapping"
      CONNECTION_PROCESSOR_DEGREE: 150
      CONNECTION_QUEUE_CAPACITY: 50000
      CONNECTION_TRANSACTION_TIMER: 5000
      CONNECTION_TIME_RETRY: 5000
      # PreDeliver
      SUBMIT_PRE_DELIVER: "preDeliver"
      SUBMIT_PRE_MESSAGE: "preMessage"
      SUBMIT_SM_RESULTS: "submit_sm_result"
      # Redis Retry Messages
      RETRY_MESSAGES_QUEUE: "sms_retry"
      # Workers Per Gateway
      WORKS_PER_GATEWAY: 10
      WORK_FOR_WORKER: 1000
      GATEWAYS_WORK_EXECUTE_EVERY: 1000
      # WebSocket
      WEBSOCKET_SERVER_ENABLED: true
      WEBSOCKET_SERVER_HOST: "{WEBSOCKET_SERVER_HOST}"
      WEBSOCKET_SERVER_PORT: 9000
      WEBSOCKET_SERVER_PATH: "/ws"
      WEBSOCKET_SERVER_RETRY_INTERVAL: 10
      WEBSOCKET_HEADER_NAME: "Authorization"
      WEBSOCKET_HEADER_VALUE: "{WEBSOCKET_HEADER_VALUE}"
      # Virtual Threads
      THREADS_VIRTUAL_ENABLED: true
      # JMX Configuration
      ENABLE_JMX: "true"
      IP_JMX: "127.0.0.1"
      JMX_PORT: "9013"
    volumes:
      - /opt/paic/smsc-docker/smpp/smpp-client-module-docker/resources/conf/logback.xml:/opt/paic/SMPP_CLIENT_MODULE/conf/logback.xml
    network_mode: host
