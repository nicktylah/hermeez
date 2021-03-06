// @flow

// App actions
export const INIT_APP = 'INIT_APP';
export const INIT_APP_FINISH = 'INIT_APP_FINISH';

export const INIT_FIREBASE = 'INIT_FIREBASE';
export const INIT_FIREBASE_SUCCESS = 'INIT_FIREBASE_SUCCESS';
export const INIT_FIREBASE_FAILURE = 'INIT_FIREBASE_FAILURE';

export const INIT_DB = 'INIT_DB';
export const INIT_DB_FAILURE = 'INIT_DB_FAILURE';
export const INIT_DB_SUCCESS = 'INIT_DB_SUCCESS';

export const AUTH_REQUEST = 'AUTH_REQUEST';
export const AUTH_SUCCESS = 'AUTH_SUCCESS';
export const AUTH_FAILURE = 'AUTH_FAILURE';

export const SIGN_OUT_REQUEST = 'SIGN_OUT_REQUEST';
export const SIGN_OUT = 'SIGN_OUT';

export const ADD_NOTIFICATION = 'ADD_NOTIFICATION';
export const REMOVE_NOTIFICATION = 'REMOVE_NOTIFICATION';

export const FOCUS_SEARCH_BAR = 'FOCUS_SEARCH_BAR';
export const BLUR_SEARCH_BAR = 'BLUR_SEARCH_BAR';

// Messages actions
// SSE for messages
export const SUBSCRIBE_MESSAGES = 'SUBSCRIBE_MESSAGES';
export const UNSUBSCRIBE_MESSAGES = 'UNSUBSCRIBE_MESSAGES';
export const MESSAGES_OPEN = 'MESSAGES_OPEN';
export const MESSAGES_ERROR = 'MESSAGES_ERROR';
export const MESSAGES_SENT = 'MESSAGES_SENT';
// Retrieve messages stream checkpoint from IndexedDB
export const GET_CHECKPOINT = 'GET_CHECKPOINT';
export const GET_CHECKPOINT_REQUEST = 'GET_CHECKPOINT_REQUEST';
export const GET_CHECKPOINT_SUCCESS = 'GET_CHECKPOINT_SUCCESS';
export const GET_CHECKPOINT_FAILURE = 'GET_CHECKPOINT_FAILURE';
// Retrieve messages from IndexedDB
export const GET_MESSAGES = 'GET_MESSAGES';
export const GET_MESSAGES_REQUEST = 'GET_MESSAGES_REQUEST';
export const GET_MESSAGES_SUCCESS = 'GET_MESSAGES_SUCCESS';
export const GET_MESSAGES_FAILURE = 'GET_MESSAGES_FAILURE';
// Add contacts to IndexedDB
export const ADD_CONTACTS = 'ADD_CONTACTS';
export const ADD_CONTACTS_REQUEST = 'ADD_CONTACTS_REQUEST';
export const ADD_CONTACTS_SUCCESS = 'ADD_CONTACTS_SUCCESS';
export const ADD_CONTACTS_FAILURE = 'ADD_CONTACTS_FAILURE';
// Add messages to IndexedDB
export const ADD_MESSAGES = 'ADD_MESSAGES';
export const ADD_MESSAGES_REQUEST = 'ADD_MESSAGES_REQUEST';
export const ADD_MESSAGES_SUCCESS = 'ADD_MESSAGES_SUCCESS';
export const ADD_MESSAGES_FAILURE = 'ADD_MESSAGES_FAILURE';
// Send message
export const SEND_MESSAGE_REQUEST = 'SEND_MESSAGE_REQUEST';
export const SEND_MESSAGE_SUCCESS = 'SEND_MESSAGE_SUCCESS';
export const SEND_MESSAGE_FAILURE = 'SEND_MESSAGE_FAILURE';

export const EDITING_MESSAGE = 'EDITING_MESSAGE';

// Threads actions
export const GET_THREADS = 'GET_THREADS';
export const GET_THREADS_REQUEST = 'GET_THREADS_REQUEST';
export const GET_THREADS_SUCCESS = 'GET_THREADS_SUCCESS';
export const GET_THREADS_FAILURE = 'GET_THREADS_FAILURE';

export const SELECT_THREAD = 'SELECT_THREAD';

export const SEARCH_THREADS = 'SEARCH_THREADS';
export const SEARCH_THREADS_SUCCESS = 'SEARCH_THREADS_SUCCESS';
export const SEARCH_THREADS_FAILURE = 'SEARCH_THREADS_FAILURE';
