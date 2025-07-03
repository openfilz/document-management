
# Building and Testing the Document Management System

This document provides instructions on how to build, configure, and test the application locally.

## Prerequisites

*   **Java 24**
*   **Maven 3.x**
*   **Docker** (for running PostgreSQL, MinIO, and Keycloak)
*   **Docker Compose**

## Building the Application

The project is a multi-module Maven project. To build all modules, run the following command from the root directory:

```bash
mvn clean install
```

This will build both the `document-management-api` and `document-management-gateway` modules.

**Note:** The `document-management-gateway` is optional. If you do not intend to use the gateway, you can build only the API module by running the following command from the root directory:
```bash
mvn clean install -pl document-management-api -am
```

## Local Testing

For local testing, you will need to run PostgreSQL, MinIO, and Keycloak. A `docker-compose.yml` file is provided in the `helm/kube-deploy` directory for this purpose.

### 1. Start Dependent Services

Navigate to the `helm/kube-deploy` directory and run the following command:

```bash
docker-compose up -d
```

This will start the following services:

*   **PostgreSQL**: A PostgreSQL database instance.
*   **MinIO**: An S3-compatible object storage service.
*   **Keycloak**: An open-source identity and access management solution.

### 2. Configure the Application

The application is configured via `application.yml` files located in the `src/main/resources` directory of each module.

#### `document-management-api`

The `document-management-api` module requires the following configuration:

*   **Database Connection**: The database connection details are configured in `application.yml`. By default, it connects to a PostgreSQL database on `localhost:5432`.
*   **Storage**: The storage can be configured to use either the local filesystem or MinIO. For local testing, you can use the local filesystem by setting `storage.type` to `local`.
*   **Keycloak**: The Keycloak integration is configured in `application.yml`. You will need to provide the Keycloak server URL and realm.

#### `document-management-gateway` (Optional)

The `document-management-gateway` module is optional and provides a single entry point to the application. It requires the following configuration:

*   **Routes**: The gateway is configured to route requests to the `document-management-api` service. The routes are defined in `application.yml`.
*   **Keycloak**: The Keycloak integration is configured in `application.yml`. You will need to provide the Keycloak server URL, realm, and client credentials.

### 3. Run the Application

Once the dependent services are running and the application is configured, you can run the `document-management-api`. Running the `document-management-gateway` is optional.

To run the `document-management-api` module, navigate to the `document-management-api` directory and run the following command:

```bash
mvn spring-boot:run
```

To run the `document-management-gateway` module (optional), navigate to the `document-management-gateway` directory and run the following command:

```bash
mvn spring-boot:run
```

### 4. Accessing the Application

If you are running the `document-management-gateway`, it will be available on port `8888`. You can access the API documentation through the gateway at `http://localhost:8888/dms-api/swagger-ui.html`.

If you are not using the gateway, you can access the `document-management-api` service directly on port `8081`. The API documentation will be available at `http://localhost:8081/swagger-ui.html`.

### 5. Running Tests

To run the unit and integration tests, run the following command from the root directory:

```bash
mvn test
```
