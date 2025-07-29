export interface DocumentBrief {
  id: string;
  name: string;
  type: string;
}

export interface FolderElementInfo {
  id: string;
  type: 'FILE' | 'FOLDER';
  name: string;
}

export interface DocumentInfo {
  type: 'FILE' | 'FOLDER';
  name: string;
  parentId?: string;
  metadata?: { [key: string]: any };
  size?: number;
}

export interface CreateFolderRequest {
  name: string;
  parentId?: string;
}

export interface FolderResponse {
  id: string;
  name: string;
  parentId?: string;
}

export interface RenameRequest {
  newName: string;
}

export interface MoveRequest {
  documentIds: string[];
  targetFolderId?: string;
  allowDuplicateFileNames?: boolean;
}

export interface CopyRequest {
  documentIds: string[];
  targetFolderId?: string;
  allowDuplicateFileNames?: boolean;
}

export interface DeleteRequest {
  documentIds: string[];
}

export interface UploadResponse {
  id: string;
  name: string;
  contentType: string;
  size: number;
}

export interface SearchByMetadataRequest {
  name?: string;
  type?: 'FILE' | 'FOLDER';
  parentFolderId?: string;
  rootOnly?: boolean;
  metadataCriteria?: { [key: string]: any };
}

export interface BreadcrumbItem {
  id?: string;
  name: string;
}

export interface FileItem extends FolderElementInfo {
  selected?: boolean;
  size?: number;
  modifiedDate?: Date;
  icon?: string;
}

export class Root implements BreadcrumbItem {

  public static INSTANCE = new Root();

  id: string = "0"
  name: string = "Root";
}