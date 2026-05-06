# Docker / Kafka commands

All commands assume you're in the `spring-crud` directory.

## Start / stop the stack

```bash
# pull images (one-time; or whenever you change tags)
docker compose pull

# start in background
docker compose up -d

# show running containers + health
docker compose ps

# follow logs (Ctrl-C to stop)
docker compose logs -f
docker compose logs -f kafka
docker compose logs -f kafka-ui

# stop containers but KEEP data volumes
docker compose stop

# stop + remove containers (data still kept since we have no volumes)
docker compose down

# stop + remove containers AND volumes (full wipe)
docker compose down -v
```

## Kafka UI

Open <http://localhost:8090> in a browser once `docker compose up -d` finishes.
Cluster name: `local`.

You'll see topics, partitions, consumer groups, and individual messages.
Useful tabs: **Topics** (browse messages), **Consumers** (current offset, lag).

## Quick health checks

```bash
# Are both containers Up?
docker compose ps

# Inspect a container's environment / config
docker inspect spring-crud-kafka | head -50

# Tail just last 50 broker log lines
docker compose logs --tail=50 kafka
```

## Use kafka CLI tools (inside the broker container)

The apache/kafka image ships the standard CLI scripts under
`/opt/kafka/bin/`. Run them via `docker exec`.

```bash
# List topics
docker exec spring-crud-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --list

# Create a topic explicitly (auto-create is on, so usually unnecessary)
docker exec spring-crud-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --topic employee.events --partitions 3 --replication-factor 1

# Describe a topic
docker exec spring-crud-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe --topic employee.events

# Tail messages live (ctrl-c to stop)
docker exec -it spring-crud-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic employee.events \
  --from-beginning \
  --property print.key=true --property key.separator=" | "

# Send a test message
docker exec -i spring-crud-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic employee.events \
  <<< 'hello-from-cli'

# Check consumer groups + lag
docker exec spring-crud-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --list

docker exec spring-crud-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group spring-crud
```

## Troubleshooting

```bash
# "Cannot connect to Docker daemon" -> start Docker Desktop, wait for whale icon
# to say "Engine running", then retry.
open -a Docker

# "image not found" -> verify the tag exists on Docker Hub:
#   https://hub.docker.com/r/apache/kafka/tags
docker pull apache/kafka:3.9.0

# Reset Kafka state completely
docker compose down -v && docker compose up -d

# Connect from your laptop to verify port 9092 is listening
nc -vz localhost 9092
```

## Why each port

| Port  | Service               | Used by                       |
|-------|-----------------------|-------------------------------|
| 9092  | Kafka PLAINTEXT       | Spring app on host            |
| 9093  | Kafka CONTROLLER      | KRaft internal quorum         |
| 9094  | Kafka INTERNAL        | container-to-container        |
| 8090  | Kafka UI              | browser                       |
| 8080  | Spring Boot           | the app (NOT in docker)       |
