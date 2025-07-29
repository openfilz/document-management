import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ElementInfo,
  DocumentInfo,
  CreateFolderRequest,
  FolderResponse,
  RenameRequest,
  MoveRequest,
  CopyRequest,
  DeleteRequest,
  UploadResponse,
  SearchByMetadataRequest
} from '../models/document.models';
import {environment} from "../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class DocumentApiService {
  private readonly baseUrl = environment.apiURL;
  private readonly authToken = 'your-jwt-token-here'; // In real app, get from auth service

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${this.authToken}`,
      'Content-Type': 'application/json'
    });
  }

  private getMultipartHeaders(): HttpHeaders {
    return new HttpHeaders({
      'Authorization': `Bearer ${this.authToken}`
    });
  }

  // Folder operations
  listFolder(folderId?: string, onlyFiles?: boolean, onlyFolders?: boolean): Observable<ElementInfo[]> {
    let params = new HttpParams();
    if (folderId) params = params.set('folderId', folderId);
    if (onlyFiles !== undefined) params = params.set('onlyFiles', onlyFiles.toString());
    if (onlyFolders !== undefined) params = params.set('onlyFolders', onlyFolders.toString());

    return this.http.get<ElementInfo[]>(`${this.baseUrl}/folders/list`, {
      headers: this.getHeaders(),
      params
    });
  }

  createFolder(request: CreateFolderRequest): Observable<FolderResponse> {
    return this.http.post<FolderResponse>(`${this.baseUrl}/folders`, request, {
      headers: this.getHeaders()
    });
  }

  renameFolder(folderId: string, request: RenameRequest): Observable<ElementInfo> {
    return this.http.put<ElementInfo>(`${this.baseUrl}/folders/${folderId}/rename`, request, {
      headers: this.getHeaders()
    });
  }

  moveFolders(request: MoveRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/folders/move`, request, {
      headers: this.getHeaders()
    });
  }

  copyFolders(request: CopyRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/folders/copy`, request, {
      headers: this.getHeaders()
    });
  }

  deleteFolders(request: DeleteRequest): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/folders`, {
      headers: this.getHeaders(),
      body: request
    });
  }

  // File operations
  renameFile(fileId: string, request: RenameRequest): Observable<ElementInfo> {
    return this.http.put<ElementInfo>(`${this.baseUrl}/files/${fileId}/rename`, request, {
      headers: this.getHeaders()
    });
  }

  moveFiles(request: MoveRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/files/move`, request, {
      headers: this.getHeaders()
    });
  }

  copyFiles(request: CopyRequest): Observable<any[]> {
    return this.http.post<any[]>(`${this.baseUrl}/files/copy`, request, {
      headers: this.getHeaders()
    });
  }

  deleteFiles(request: DeleteRequest): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/files`, {
      headers: this.getHeaders(),
      body: request
    });
  }

  // Document operations
  getDocumentInfo(documentId: string, withMetadata?: boolean): Observable<DocumentInfo> {
    let params = new HttpParams();
    if (withMetadata !== undefined) params = params.set('withMetadata', withMetadata.toString());

    return this.http.get<DocumentInfo>(`${this.baseUrl}/documents/${documentId}/info`, {
      headers: this.getHeaders(),
      params
    });
  }

  downloadDocument(documentId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/documents/${documentId}/download`, {
      headers: this.getHeaders(),
      responseType: 'blob'
    });
  }

  downloadMultipleDocuments(documentIds: string[]): Observable<Blob> {
    return this.http.post(`${this.baseUrl}/documents/download-multiple`, documentIds, {
      headers: this.getHeaders(),
      responseType: 'blob'
    });
  }

  uploadDocument(file: File, parentFolderId?: string, metadata?: string, allowDuplicateFileNames?: boolean): Observable<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    if (parentFolderId) formData.append('parentFolderId', parentFolderId);
    if (metadata) formData.append('metadata', metadata);

    let params = new HttpParams();
    if (allowDuplicateFileNames !== undefined) {
      params = params.set('allowDuplicateFileNames', allowDuplicateFileNames.toString());
    }

    return this.http.post<UploadResponse>(`${this.baseUrl}/documents/upload`, formData, {
      headers: this.getMultipartHeaders(),
      params
    });
  }

  searchDocumentIdsByMetadata(request: SearchByMetadataRequest): Observable<string[]> {
    return this.http.post<string[]>(`${this.baseUrl}/documents/search/ids-by-metadata`, request, {
      headers: this.getHeaders()
    });
  }
}