@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM == This script is a Windows Batch translation of the official KinD local
REM == registry setup script, with added port mappings for Ingress access.
REM == https://kind.sigs.k8s.io/docs/user/local-registry/
REM ============================================================================

REM --- Configuration ---
set "reg_name=kind-registry"
set "reg_port=5001"
set "KIND_CLUSTER_CONFIG_TMP=kind-cluster-config.tmp.yaml"
set "HOSTS_CONFIG_TMP=hosts.tmp.toml"
set "CM_CONFIG_TMP=cm.tmp.yaml"

echo [INFO] Setting up KinD cluster with local registry '%reg_name%' on port '%reg_port%'...

REM ----------------------------------------------------------------------------
REM 1. Create registry container unless it already exists
REM ----------------------------------------------------------------------------
echo.
echo [1/5] Checking for registry container '%reg_name%'...

set "IS_RUNNING="
for /f "delims=" %%i in ('docker inspect -f "{{.State.Running}}" %reg_name% 2^>nul') do set "IS_RUNNING=%%i"

if not "%IS_RUNNING%"=="true" (
    echo [INFO] Registry not running. Starting container...
    docker run -d --restart=always -p "127.0.0.1:%reg_port%:5000" --name "%reg_name%" registry:2
) else (
    echo [INFO] Registry container is already running.
)

REM ----------------------------------------------------------------------------
REM 2. Create kind cluster with containerd registry config dir and Ingress ports
REM BATCH TRANSLATION NOTE: Batch has no 'here-docs', so we create a temp file.
REM ----------------------------------------------------------------------------
echo.
echo [2/5] Creating KinD cluster configuration...
(
    echo kind: Cluster
    echo apiVersion: kind.x-k8s.io/v1alpha4
    echo nodes:
    echo - role: control-plane
    echo   kubeadmConfigPatches:
    echo   - ^|-
    echo     kind: InitConfiguration
    echo     nodeRegistration:
    echo       kubeletExtraArgs:
    echo         node-labels: "ingress-ready=true"
    echo   extraPortMappings:
    echo   - containerPort: 80
    echo     hostPort: 80
    echo     protocol: TCP
    echo   - containerPort: 443
    echo     hostPort: 443
    echo     protocol: TCP
    echo containerdConfigPatches:
    echo - ^|-
    echo   [plugins."io.containerd.grpc.v1.cri".registry]
    echo     config_path = "/etc/containerd/certs.d"
) > %KIND_CLUSTER_CONFIG_TMP%

echo [INFO] Creating KinD cluster... (This will fail if a cluster already exists)
kind create cluster --config=%KIND_CLUSTER_CONFIG_TMP%
del %KIND_CLUSTER_CONFIG_TMP%

REM ----------------------------------------------------------------------------
REM 3. Add the registry config to the nodes
REM BATCH TRANSLATION NOTE: We create a temp hosts.toml file and use 'docker cp'.
REM ----------------------------------------------------------------------------
echo.
echo [3/5] Configuring nodes to use the local registry...
set "REGISTRY_DIR=/etc/containerd/certs.d/localhost:%reg_port%"

echo [INFO] Creating temporary hosts.toml file...
(
    echo [host."http://%reg_name%:5000"]
) > %HOSTS_CONFIG_TMP%

for /f "delims=" %%N in ('kind get nodes') do (
    echo [INFO] Configuring node: %%N
    docker exec "%%N" mkdir -p "%REGISTRY_DIR%"
    docker cp "%HOSTS_CONFIG_TMP%" "%%N:%REGISTRY_DIR%/hosts.toml"
)
del %HOSTS_CONFIG_TMP%

REM ----------------------------------------------------------------------------
REM 4. Connect the registry to the cluster network
REM ----------------------------------------------------------------------------
echo.
echo [4/5] Connecting registry to the 'kind' network...

set "IS_CONNECTED="
for /f "delims=" %%i in ('docker inspect -f "{{json .NetworkSettings.Networks.kind}}" %reg_name%') do set "IS_CONNECTED=%%i"

if "%IS_CONNECTED%"=="null" (
    docker network connect "kind" "%reg_name%"
    echo [INFO] Network connected.
) else (
    echo [INFO] Registry is already connected to the 'kind' network.
)

REM ----------------------------------------------------------------------------
REM 5. Document the local registry in a ConfigMap
REM BATCH TRANSLATION NOTE: Using a temp file for the kubectl apply command.
REM ----------------------------------------------------------------------------
echo.
echo [5/5] Creating kube-public/local-registry-hosting ConfigMap...
(
    echo apiVersion: v1
    echo kind: ConfigMap
    echo metadata:
      echo name: local-registry-hosting
      echo namespace: kube-public
    echo data:
      echo localRegistryHosting.v1: ^|
      echo   host: "localhost:%reg_port%"
      echo   help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
) > %CM_CONFIG_TMP%

kubectl apply -f %CM_CONFIG_TMP%
del %CM_CONFIG_TMP%

echo.
echo ============================================================================
echo == SETUP COMPLETE
echo ============================================================================
echo The KinD cluster is running and configured to use the local registry.
echo Ports 80 and 443 are now forwarded to your host machine for Ingress access.
echo.

endlocal