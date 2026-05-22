k8sgpt serve --port 8082 --mcp --mcp-http --mcp-port 8089
kubectl -n mem0 port-forward svc/mem0-api 8888:8000
kubectl -n mem0 port-forward svc/mem0-dashboard 3000:3000

docker build --build-arg MODULE=supervisor-agent --build-arg PORT=8080 \
-t dev-apaas-harbor-app.mis.bcs/ai/paas-agent-supervisor:2026052201 .

docker build --build-arg MODULE=guide-sub-agent --build-arg PORT=8081 \
-t dev-apaas-harbor-app.mis.bcs/ai/paas-agent-guide:2026052201 .

docker build --build-arg MODULE=diagnosis-sub-agent --build-arg PORT=8082 \
-t dev-apaas-harbor-app.mis.bcs/ai/paas-agent-diagnosis:2026052201 .

docker build --build-arg MODULE=analyze-sub-agent --build-arg PORT=8084 \
-t dev-apaas-harbor-app.mis.bcs/ai/paas-agent-analyze:2026052201 .

docker build --build-arg MODULE=platform-mcp-server --build-arg PORT=8083 \
-t dev-apaas-harbor-app.mis.bcs/ai/platform-mcp-server:latest .




docker save -o paas-agents-all.tar \
dev-apaas-harbor-app.mis.bcs/ai/paas-agent-supervisor:2026052201 \
dev-apaas-harbor-app.mis.bcs/ai/paas-agent-diagnosis:2026052201 \
dev-apaas-harbor-app.mis.bcs/ai/paas-agent-analyze:2026052201 \
dev-apaas-harbor-app.mis.bcs/ai/paas-agent-guide:2026052201 \
dev-apaas-harbor-app.mis.bcs/ai/k8sgpt:0.4.33