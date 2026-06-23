k8sgpt serve --port 8082 --mcp --mcp-http --mcp-port 8089
kubectl -n mem0 port-forward svc/mem0-api 8888:8000
kubectl -n mem0 port-forward svc/mem0-dashboard 3000:3000


docker build --build-arg MODULE=diagnosis-sub-agent --build-arg PORT=8082 \
-t dev-apaas-harbor-app.mis.bcs/ai/paas-agent-diagnosis:2.0.5 . 
docker build --build-arg MODULE=guide-sub-agent --build-arg PORT=8081 \
-t dev-apaas-harbor-app.mis.bcs/ai/paas-agent-guide:2.0.5 . 
docker build --build-arg MODULE=change-mcp-server --build-arg PORT=10001 \
-t dev-apaas-harbor-app.mis.bcs/ai/change-mcp-server:2.0.5 .

docker build -t dev-apaas-harbor-app.mis.bcs/ai/paas-agent-frontend:2.0.5 .


docker save -o paas-agents-all.tar \
dev-apaas-harbor-app.mis.bcs/ai/paas-agent-diagnosis:2.0.5 \
dev-apaas-harbor-app.mis.bcs/ai/paas-agent-guide:2.0.5 \
dev-apaas-harbor-app.mis.bcs/ai/change-mcp-server:2.0.5 \
dev-apaas-harbor-app.mis.bcs/ai/nacos-server:v3.2.2-slim
dev-apaas-harbor-app.mis.bcs/ai/paas-agent-frontend:2.0.5 \


dev-apaas-harbor-app.mis.bcs/ai/kom:0.2.71 \
dev-apaas-harbor-app.mis.bcs/ai/k8sgpt:0.4.33 \
