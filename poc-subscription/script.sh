#!/bin/bash

# Apply the Kubernetes configuration
kubectl apply -f kamelets/hip-cxf-source.kamelet.yaml
kubectl apply -f kamelets/hip-soap-processor-action.kamelet.yaml
kubectl apply -f kamelets/hip-oracle-source.kamelet.yaml
kubectl apply -f kamelets/hip-router-processor-action.kamelet.yaml
kubectl apply -f kamelets/hip-hl7messagepreparation-processor-action.kamelet.yaml
kubectl apply -f kamelets/hip-updateeventstatus-processor-action.kamelet.yaml

kamel delete hip-cxf-pipe
kamel delete hip-oracle-to-tcp-rabbitmq-pipe
kamel delete hip-oracle-to-soap-rabbitmq-pipe
kamel delete hip-rabbitmq-to-tcp-pipe

kubectl apply -f pipes/hip-cxf-pipe.yaml
kubectl apply -f pipes/hip-oracle-to-soap-rabbitmq-pipe.yaml
kubectl apply -f pipes/hip-oracle-to-tcp-rabbitmq-pipe.yaml
kubectl apply -f pipes/hip-rabbitmq-to-tcp-pipe.yaml

# Wait for 40 seconds to allow resources to be created and ready
sleep 5

# Wait for the pod to be created and running
echo "Waiting for the pod to be created and running..."
while true; do
  POD_NAME=$(kubectl get pods --no-headers | awk '/^hip-cxf/ {print $1}')
  if [ -n "$POD_NAME" ]; then
    echo "Pod found: $POD_NAME"
    break
  fi
  sleep 5
done

# Wait until the pod is in the "Running" state and its containers are ready
while true; do
  POD_STATUS=$(kubectl get pod "$POD_NAME" -o jsonpath='{.status.phase}')
  if [ "$POD_STATUS" == "Running" ]; then
    READY_CONTAINERS=$(kubectl get pod "$POD_NAME" -o jsonpath='{.status.containerStatuses[0].ready}')
    if [ "$READY_CONTAINERS" == "true" ]; then
      echo "Pod is running and ready: $POD_NAME"
      break
    fi
  fi
  echo "Waiting for pod to be ready..."
  sleep 5
done

# Port-forwarding
echo "Pod is ready. Performing port-forward..."
kubectl port-forward "$POD_NAME" 8090:8090