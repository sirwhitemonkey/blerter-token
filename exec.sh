
#!/bin/bash

project=sirwhitemonkey
appName=blerter-token
tag=test

if [ "$1" != "" ] && [ "$2" != "" ]; then

 case "$2" in
         build)
        if [ "$3" != "" ]; then
        	   tag=$3
        	   
        	   docker build --no-cache -t "${appName}" .
           docker tag "${appName}" "${project}/${appName}:${tag}"
           docker push "${project}/${appName}:${tag}"
        
        else
           docker build --no-cache -t "${appName}" .
           docker tag "${appName}" "${project}/${appName}"
           gcloud docker -- push "${project}/${appName}"
           
        fi

        ;;
        start)
        if [ "$3" != "" ]; then
        	  tag=$3
        	  imageTag="${project}/${appName}:${tag}"
           sed -i.bak "s#${project}/${appName}#${imageTag}#" config.yaml
           kubectl create -f config.yaml --namespace=$1
           imageTag="${project}/${appName}"
           sed -i.bak "s#${project}/${appName}:${tag}#${imageTag}#" config.yaml
           rm config.yaml.bak
        
        else
          kubectl create -f config.yaml --namespace=$1 
        fi
        ;;
        stop)
        kubectl delete -f config.yaml --namespace=$1 --grace-period=0

        ;;
        *)
        echo "Usage: {start|stop|build}"
        ;;
    esac

else
    echo "Error: syntax <namespace> <build|start|stop> <tag>"
fi
