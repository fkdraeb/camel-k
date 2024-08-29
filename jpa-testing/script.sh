#!/bin/bash

# Apply the Kubernetes configuration
kubectl apply -f hip-jpa-processor-action.kamelet.yaml
kubectl apply -f ../generic-kamelets/netty-http-source.kamelet.yaml
# Delete the existing pipe if it exists
kamel delete hip-jpa-testing-pipe

sleep 5

# Apply the new pipe configuration
kubectl apply -f hip-jpa-testing-pipe.yaml

# Wait for the pod to be created and running
echo "Waiting for the pod to be created and running..."
while true; do
  POD_NAME=$(kubectl get pods --no-headers | awk '/^hip-jpa-testing-pipe/ {print $1}')
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
kubectl port-forward "$POD_NAME" 8092:8092
