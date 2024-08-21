#!/bin/bash

# Apply the Kubernetes configuration
kubectl apply -f hip-cxf-source.kamelet.yaml
kubectl apply -f hip-oracle-source.kamelet.yaml
kubectl apply -f hip-router-processor-action.kamelet.yaml
kubectl apply -f hip-hl7messagepreparation-processor-action.kamelet.yaml

#kamel delete hip-oracle-to-tcp-rabbitmq-pipe
kamel delete hip-rabbitmq-to-tcp-pipe

#kubectl apply -f hip-cxf-pipe.yaml
#kubectl apply -f hip-oracle-to-soap-rabbitmq-pipe.yaml
#kubectl apply -f hip-oracle-to-tcp-rabbitmq-pipe.yaml
kubectl apply -f hip-rabbitmq-to-tcp-pipe.yaml

# Wait for 40 seconds to allow resources to be created and ready
#sleep 75s

# Get the name of the pod that starts with 'hip-cxf'
POD_NAME=$(kubectl get pods --no-headers | awk '/^hip-cxf/ {print $1}')

# Check if POD_NAME is empty
#if [ -z "$POD_NAME" ]; then
#  echo "No pod found that starts with 'hip-cxf'"
#  exit 1
#fi

kubectl port-forward "$POD_NAME" 8090:8090
