'use strict';

function showApp(user) {
  signedIn = true;
  document.querySelector('#login').classList.add('hidden');
  document.querySelector('#app').classList.remove('hidden');
  document.querySelector('#identity').textContent = user.username;
  window.scrollTo(0, 0);
  live.start();
  refresh();
}

function showLogin() {
  live.stop();
  signedIn = false;
  document.querySelector('#login').classList.remove('hidden');
  document.querySelector('#app').classList.add('hidden');
}
