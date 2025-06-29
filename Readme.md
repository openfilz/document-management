# Welcome to **_OpenFilz_** Document Management - a Java API to manage files and folders.

## General concepts
A file can belong to zero or one folder.
Folders structure are hierarchical : a folder can belong to zero or one parent folder.
All actions can be performed using the Rest API exposed by the Spring Boot Backend.

The possible actions are the following :
- Create a folder providing optionnaly the ID of the parent folder inside which the new folder has to be created. If no parent folder ID is provided, then the new folder is created at the root of the hierarchy.
- Move a set of files into an existing folder, providing an array of the IDs of the files to be moved and the ID of the target folder
- Copy a set of files into an existing folder, providing an array of the IDs of the files to be copied and the ID of the target folder
- Move a set of folders (and their contents) into an existing folder, providing an array of the IDs of the folders to be moved and the ID of the target folder
- Copy a set of folders (and their contents) into an existing folder, providing an array of the IDs of the folders to be copied and the ID of the target folder
- Rename a file providing the ID of the file
- Rename a folder providing the ID of the folder
- Delete a set of files providing an array of the IDs of the files to be deleted : the files will be deleted from the physical storage (local filesystem or S3) and from the database
- Delete a set of folders providing an array of the IDs of the folders to be deleted : the folders will be deleted from the physical storage (local filesystem or S3) and from the database
- Upload a document with or without metada and return the generated document ID
- Upload multiple documents with or without metada and return the generated document IDs
- Replace a document (file or folder) and/or its metadata using the document ID and optionally the new metadata to be updated
- Update metadata of a document (file or folder) providing the ID of the document and an array of the metadata to updated
- Delete some metadata of a document (file or folder) providing the ID of the document and an array of the metadata keys to delete
- Download one document using the document ID
- Download multiple documents within a ZIP file, providing an array of document IDs
- Search documents IDs using metadata
- Search metadata using the document ID and (optionally) the metadata keys array to be retrieved : if no metadata keys array is provided, all metadata of the requested document are returned

The document storage layer of the API is customizable : it can be a local filesystem or a MinIo S3 backend.
The metadata are stored in a PostGreSQL Database in a JSONB column mapped to the "metadata" field of the "Document" entity.

The DTO must use Java record instead of pojos.

Use lombok annotations when applicable.

The Rest APIs must be implemented using Spring Webflux, and all code must be reactive : from the API (with WebFlux) to the Database (using R2DBC).

When an endpoint uses a service that request access to the storage layer, the actions must be processed in the following order : 
1. Storage layer processing
2. Database processing to store document information and metadata
3. Audit of the action performed (audit trail is stored in the database)

To avoid complex management of file & folders names and hierarchy : decision has been taken to store on the storage layer only files, not folders. Folders are created only in the database and files location within folders is defined in the database.

All endpoints of the document management API must be self-documented using swagger UI and unit tests using Junit must cover all source code.

The spring cloud gateway will receive all client requests and has the following roles :
1. it is the only central endpoint that listen for the client requests
2. it manages authentication : it is linked to a keycloak server (autorization server and identity povider) that will generate or validate the JWT token needed to connect the document management API
3. if the token is valid, the api gateway redirect all incoming client requests to the document management API

At each request to any endpoint of the document api, the JWT token must be validated by the API using keycloak validation endpoint.

All actions must be audited : 
- Retrieve User Principal from JWT token
- Trace all Write accesses to the file storage layer (write accesses on the filesystem or S3)
- Trace all Write accesses to the database layer


