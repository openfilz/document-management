#!/bin/sh

openshift=false
create_pv_pvc=true

if [ "$openshift" = true ]
then
  echo "Openshift deployment"
  storage_class=thin-csi
  kubectl_command=(oc)
  PATH=../../../kube-utils:$PATH
  pvc_name=ged-volume-pvc
  namespace=ged-lab
else
  echo "non Openshift deployment"
  storage_class=manual
  mount_path=/tmp/kube-storage
  kubectl_command=(kubectl)
  pv_name=dms-volume-pv
  storage_total_capacity=20Mi
  pvc_name=dms-volume-pvc
  namespace=openfilz
fi

# Script to create a PVC given its size, name, namespace and helm_chart_folder where we expect to find a values-template.yaml file where the #pv_name# token is present
# The values-template.yaml will be copied to values.yaml and the #pv_name# token will be replaced with the name of the PV dynamically allocated within the PVC creation

size=10Mi
helm_chart_folder=helm-document-management-api

if [ "$openshift" = false ] && [ "$create_pv_pvc" = true ]
then

  echo "Delete the existing PV (if already exists)"
  "$kubectl_command" delete pv $pv_name

  echo "Copy PV template"
  cp pv-template.yaml pv.yaml

  echo "Replace values in pv.yaml"
  sed -i 's~#mount_path#~'"$mount_path"'~g' pv.yaml
  sed -i 's/#pv_name#/'"$pv_name"'/g' pv.yaml
  sed -i 's/#namespace#/'"$namespace"'/g' pv.yaml
  sed -i 's/#storageclass#/'"$storage_class"'/g' pv.yaml
  sed -i 's/#storage_total_capacity#/'"$storage_total_capacity"'/g' pv.yaml

  echo "PV creation...."
  # Creation of the PV
  "$kubectl_command" create -f pv.yaml

  echo "Sleeping 5 seconds..."
  sleep 5
fi

if [ "$create_pv_pvc" = true ]
then
  # Delete the existing PVC (if already exists)
  echo "Delete the existing PVC (if already exists)"
  "$kubectl_command" delete pvc $pvc_name --namespace $namespace

  # Create the pvc.yaml file from the template
  echo "Copy PVC template"
  cp pvc-template.yaml pvc.yaml

  # Replace in the pvc.yaml file the tokens with the values chosen for size, name, namespace
  echo "Replace values in pvc.yaml"
  sed -i 's/#size#/'"$size"'/g' pvc.yaml
  sed -i 's/#pvc_name#/'"$pvc_name"'/g' pvc.yaml
  sed -i 's/#namespace#/'"$namespace"'/g' pvc.yaml
  sed -i 's/#storageclass#/'"$storage_class"'/g' pvc.yaml

  # Creation of the PVC
  echo "Create the PVC"
  "$kubectl_command" create -f pvc.yaml

  if [ "$openshift" = true ]
  then
    echo "Sleeping 5 seconds..."
    sleep 5

    # Retrieving the name of the PV associated with the PVC just created and storing it into the pv_name variable
    pv_name=$("$kubectl_command" get pvc --namespace $namespace | grep $pvc_name |  awk '{print $3}')

    echo 'Created PV name is ' $pv_name
  fi
fi

# Create the values.yaml file from the template
echo "Create the values.yaml file from the template"
cp $helm_chart_folder/values-template.yaml $helm_chart_folder/values.yaml

# Replace in the value file the #pv_name# token with the good PV name
echo "Replace in the value.yaml file"
sed -i 's/#pv_name#/'"$pv_name"'/g' $helm_chart_folder/values.yaml
sed -i 's/#namespace#/'"$namespace"'/g' $helm_chart_folder/values.yaml

echo "The tokens have been replaced in $helm_chart_folder/values.yaml"