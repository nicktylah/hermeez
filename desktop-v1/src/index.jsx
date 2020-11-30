import React from 'react';
import firebase from 'firebase';
import {render} from 'react-dom';
import App from './components/app.jsx';
window.firebase = firebase;

// Initialize Firebase
console.log('Initializing Firebase');
const config = {
  apiKey: 'AIzaSyAREY84X88hIQJAN_Mm8Z6ZQPSPoH2XnnY',
  authDomain: 'android-messages-6c275.firebaseapp.com',
  databaseURL: 'https://android-messages-6c275.firebaseio.com',
  storageBucket: 'android-messages-6c275.appspot.com',
  messagingSenderId: '578754319051'
};

firebase.initializeApp(config);
console.log('Firebase initialized');

const rootElem = document.getElementById('app');

render(<App />, rootElem);
