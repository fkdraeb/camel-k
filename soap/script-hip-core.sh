kamel reset
kubectl apply -f soap-to-rabbitmq-pipe.yaml
kubectl apply -f soap-process-action.kamelet.yaml
kamel delete soap-response-pipe
kubectl apply -f soap-response-pipe.yaml
