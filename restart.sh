docker-compose down --volumes --remove-orphans --rmi local
docker volume rm grafana-storage postgres_data
docker network prune -f
docker network ls \
  --filter "driver=bridge" \
  --format '{{.Name}}' \
  | grep -vE '^(bridge|host|none)$' \
  | xargs -r docker network rm
mvn clean package
docker-compose build --no-cache
docker-compose up -d
