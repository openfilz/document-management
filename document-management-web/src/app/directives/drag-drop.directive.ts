import { Directive, EventEmitter, HostBinding, HostListener, Output } from '@angular/core';

@Directive({
  selector: '[appDragDrop]',
  standalone: true
})
export class DragDropDirective {
  @Output() filesDropped = new EventEmitter<FileList>();
  @Output() fileOverChange = new EventEmitter<boolean>();
  @HostBinding('class.file-over') fileOver: boolean = false;

  @HostListener('dragover', ['$event'])
  onDragOver(evt: DragEvent) {
    evt.preventDefault();
    evt.stopPropagation();
    this.fileOver = true;
    this.fileOverChange.emit(true);
  }

  @HostListener('dragleave', ['$event'])
  onDragLeave(evt: DragEvent) {
    evt.preventDefault();
    evt.stopPropagation();
    this.fileOver = false;
    this.fileOverChange.emit(false);
  }

  @HostListener('drop', ['$event'])
  onDrop(evt: DragEvent) {
    evt.preventDefault();
    evt.stopPropagation();
    this.fileOver = false;
    this.fileOverChange.emit(false);
    if (evt.dataTransfer?.files) {
      this.filesDropped.emit(evt.dataTransfer.files);
    }
  }
}