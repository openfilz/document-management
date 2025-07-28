import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ToolbarComponent } from './components/toolbar/toolbar.component';
import { BreadcrumbComponent } from './components/breadcrumb/breadcrumb.component';
import { FileGridComponent } from './components/file-grid/file-grid.component';
import { FileListComponent } from './components/file-list/file-list.component';
import { UploadZoneComponent } from './components/upload-zone/upload-zone.component';
import { CreateFolderDialogComponent } from './dialogs/create-folder-dialog/create-folder-dialog.component';
import { RenameDialogComponent, RenameDialogData } from './dialogs/rename-dialog/rename-dialog.component';

import { DocumentApiService } from './services/document-api.service';
import { FileIconService } from './services/file-icon.service';

import { 
  FileItem, 
  BreadcrumbItem, 
  FolderElementInfo,
  CreateFolderRequest,
  RenameRequest,
  DeleteRequest
} from './models/document.models';
import {MatIcon} from "@angular/material/icon";

@Component({
  selector: 'app-main',
  standalone: true,
  templateUrl: './main.component.html',
  styleUrls: ['./main.component.css'],
  imports: [
    CommonModule,
    MatDialogModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    ToolbarComponent,
    BreadcrumbComponent,
    FileGridComponent,
    FileListComponent,
    UploadZoneComponent,
    MatIcon
  ],
})
export class MainComponent implements OnInit {
  viewMode: 'grid' | 'list' = 'grid';
  loading = false;
  showUploadZone = false;
  
  items: FileItem[] = [];
  breadcrumbs: BreadcrumbItem[] = [];
  currentFolderId?: string;

  constructor(
    private documentApi: DocumentApiService,
    private fileIconService: FileIconService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit() {
    this.loadFolder();
  }

  get hasSelectedItems(): boolean {
    return this.items.some(item => item.selected);
  }

  get selectedItems(): FileItem[] {
    return this.items.filter(item => item.selected);
  }

  loadFolder(folderId?: string) {
    this.loading = true;
    this.currentFolderId = folderId;

    this.documentApi.listFolder(folderId).subscribe({
      next: (response: FolderElementInfo[]) => {
        this.items = response.map(item => ({
          ...item,
          selected: false,
          icon: this.fileIconService.getFileIcon(item.name, item.type)
        }));
        this.showUploadZone = this.items.length === 0;
        this.loading = false;
        this.updateBreadcrumbs(folderId);
      },
      error: (error) => {
        console.error('Failed to load folder:', error);
        this.snackBar.open('Failed to load folder contents', 'Close', { duration: 3000 });
        this.loading = false;
      }
    });
  }

  updateBreadcrumbs(folderId?: string) {
    // In a real app, you'd track the full path
    this.breadcrumbs = [];
    if (folderId) {
      // Add breadcrumb items based on current path
      // This is a simplified implementation
    }
  }

  onViewModeChange(mode: 'grid' | 'list') {
    this.viewMode = mode;
  }

  onCreateFolder() {
    const dialogRef = this.dialog.open(CreateFolderDialogComponent, {
      width: '400px',
      data: {}
    });

    dialogRef.afterClosed().subscribe(folderName => {
      if (folderName) {
        const request: CreateFolderRequest = {
          name: folderName,
          parentId: this.currentFolderId
        };

        this.documentApi.createFolder(request).subscribe({
          next: () => {
            this.snackBar.open('Folder created successfully', 'Close', { duration: 3000 });
            this.loadFolder(this.currentFolderId);
          },
          error: (error) => {
            console.error('Failed to create folder:', error);
            this.snackBar.open('Failed to create folder', 'Close', { duration: 3000 });
          }
        });
      }
    });
  }

  onUploadFiles() {
    const input = document.createElement('input');
    input.type = 'file';
    input.multiple = true;
    input.onchange = (event) => {
      const files = (event.target as HTMLInputElement).files;
      if (files) {
        this.handleFileUpload(files);
      }
    };
    input.click();
  }

  onFilesSelected(files: FileList) {
    this.handleFileUpload(files);
  }

  private handleFileUpload(files: FileList) {
    Array.from(files).forEach(file => {
      this.documentApi.uploadDocument(file, this.currentFolderId).subscribe({
        next: () => {
          this.snackBar.open(`${file.name} uploaded successfully`, 'Close', { duration: 3000 });
          this.loadFolder(this.currentFolderId);
        },
        error: (error) => {
          console.error(`Failed to upload ${file.name}:`, error);
          this.snackBar.open(`Failed to upload ${file.name}`, 'Close', { duration: 3000 });
        }
      });
    });
  }

  onItemClick(item: FileItem) {
    // Toggle selection
    item.selected = !item.selected;
  }

  onItemDoubleClick(item: FileItem) {
    if (item.type === 'FOLDER') {
      this.loadFolder(item.id);
    } else {
      this.onDownloadItem(item);
    }
  }

  onSelectionChange(event: { item: FileItem, selected: boolean }) {
    event.item.selected = event.selected;
  }

  onSelectAll(selected: boolean) {
    this.items.forEach(item => item.selected = selected);
  }

  onRenameItem(item: FileItem) {
    const dialogRef = this.dialog.open(RenameDialogComponent, {
      width: '400px',
      data: { name: item.name, type: item.type } as RenameDialogData
    });

    dialogRef.afterClosed().subscribe(newName => {
      if (newName) {
        const request: RenameRequest = { newName };
        
        const renameObservable = item.type === 'FOLDER' 
          ? this.documentApi.renameFolder(item.id, request)
          : this.documentApi.renameFile(item.id, request);

        renameObservable.subscribe({
          next: () => {
            this.snackBar.open('Item renamed successfully', 'Close', { duration: 3000 });
            this.loadFolder(this.currentFolderId);
          },
          error: (error) => {
            console.error('Failed to rename item:', error);
            this.snackBar.open('Failed to rename item', 'Close', { duration: 3000 });
          }
        });
      }
    });
  }

  onDownloadItem(item: FileItem) {
    if (item.type === 'FILE') {
      this.documentApi.downloadDocument(item.id).subscribe({
        next: (blob) => {
          const url = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = item.name;
          document.body.appendChild(a);
          a.click();
          document.body.removeChild(a);
          window.URL.revokeObjectURL(url);
        },
        error: (error) => {
          console.error('Failed to download file:', error);
          this.snackBar.open('Failed to download file', 'Close', { duration: 3000 });
        }
      });
    }
  }

  onMoveItem(item: FileItem) {
    // TODO: Implement move functionality with folder selection dialog
    this.snackBar.open('Move functionality coming soon', 'Close', { duration: 3000 });
  }

  onCopyItem(item: FileItem) {
    // TODO: Implement copy functionality with folder selection dialog
    this.snackBar.open('Copy functionality coming soon', 'Close', { duration: 3000 });
  }

  onDeleteItem(item: FileItem) {
    if (confirm(`Are you sure you want to delete "${item.name}"?`)) {
      this.deleteItems([item]);
    }
  }

  onDownloadSelected() {
    const selectedItems = this.selectedItems;
    if (selectedItems.length === 1 && selectedItems[0].type === 'FILE') {
      this.onDownloadItem(selectedItems[0]);
    } else if (selectedItems.length > 1) {
      const documentIds = selectedItems.map(item => item.id);
      this.documentApi.downloadMultipleDocuments(documentIds).subscribe({
        next: (blob) => {
          const url = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = 'documents.zip';
          document.body.appendChild(a);
          a.click();
          document.body.removeChild(a);
          window.URL.revokeObjectURL(url);
        },
        error: (error) => {
          console.error('Failed to download files:', error);
          this.snackBar.open('Failed to download files', 'Close', { duration: 3000 });
        }
      });
    }
  }

  onDeleteSelected() {
    const selectedItems = this.selectedItems;
    if (selectedItems.length > 0) {
      const itemNames = selectedItems.map(item => item.name).join(', ');
      if (confirm(`Are you sure you want to delete: ${itemNames}?`)) {
        this.deleteItems(selectedItems);
      }
    }
  }

  private deleteItems(items: FileItem[]) {
    const request: DeleteRequest = {
      documentIds: items.map(item => item.id)
    };

    const folders = items.filter(item => item.type === 'FOLDER');
    const files = items.filter(item => item.type === 'FILE');

    const deleteObservables = [];
    
    if (folders.length > 0) {
      deleteObservables.push(this.documentApi.deleteFolders({ documentIds: folders.map(f => f.id) }));
    }
    
    if (files.length > 0) {
      deleteObservables.push(this.documentApi.deleteFiles({ documentIds: files.map(f => f.id) }));
    }

    deleteObservables.forEach(observable => {
      observable.subscribe({
        next: () => {
          this.snackBar.open('Items deleted successfully', 'Close', { duration: 3000 });
          this.loadFolder(this.currentFolderId);
        },
        error: (error) => {
          console.error('Failed to delete items:', error);
          this.snackBar.open('Failed to delete items', 'Close', { duration: 3000 });
        }
      });
    });
  }

  onNavigate(path: string) {
    if (path === '/') {
      this.loadFolder();
    } else {
      // Navigate to specific folder based on path
      // This would need proper path-to-ID mapping in a real app
    }
  }

  onSearch(query: string) {
    if (query.trim()) {
      // TODO: Implement search functionality
      this.snackBar.open('Search functionality coming soon', 'Close', { duration: 3000 });
    }
  }
}