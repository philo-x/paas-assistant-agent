k8sgpt serve --port 8082 --mcp --mcp-http --mcp-port 8089
kubectl -n mem0 port-forward svc/mem0-api 8888:8000
kubectl -n mem0 port-forward svc/mem0-dashboard 3000:3000