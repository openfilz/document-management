** General rules **

- When we implement a service that request the storageService : perform 
  1. Call StorageService
  2. Call DocumentRepo
  3. Call AuditService

- To avoid complex management of file & folders names : we store on the storage layer only UUIDs : File & Folder names are maintained only at the DB level~~~~