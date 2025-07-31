import { Component } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { MainComponent } from './app/main.component';

@Component({
  selector: 'app-root',
  template: `<app-main></app-main>`,
  standalone: true,
  imports: [MainComponent]
})
export class App {}

bootstrapApplication(App, {
  providers: [
    provideAnimations(),
    provideHttpClient()
  ]
});