#!/bin/bash
# setup-redis.sh: Starts a Redis container for the rate limiter service.

# Check if Docker is installed
if ! [ -x "$(command -v docker)" ]; then
  echo 'Error: docker is not installed.' >&2
  exit 1
fi

CONTAINER_NAME="rate-limiter-redis"

# Stop and remove existing container with the same name
if [ "$(docker ps -q -f name=$CONTAINER_NAME)" ]; then
    echo "Stopping and removing existing container: $CONTAINER_NAME"
    docker stop $CONTAINER_NAME
    docker rm $CONTAINER_NAME
fi

# Run a new Redis container
echo "Starting new Redis container: $CONTAINER_NAME"
docker run --name $CONTAINER_NAME -p 6379:6379 -d redis:7

# Verify connection
echo "Verifying Redis connection..."
sleep 2 # Wait for Redis to start up
if docker exec $CONTAINER_NAME redis-cli PING | grep -q 'PONG'; then
  echo "Redis is up and running!"
else
  echo "Failed to connect to Redis."
  exit 1
fi
