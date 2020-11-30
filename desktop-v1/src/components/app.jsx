// @flow
import React, {Component, PropTypes} from 'react';
// const {app} = require('electron');
import firebase from 'firebase';
import _ from 'lodash';
import path from 'path';
import Login from './login';
import Password from './login/password';
import ConversationsContainer from './conversations/container';
import MessagesContainer from './messages/container';
import {getRandomColor} from '../lib/colors';
import decrypt from '../lib/encyption';
import backoff from '../lib/backoff';
firebase.database.INTERNAL.forceWebSockets();
import {googleSignIn} from '../../auth';

const config = {
  projectId: 'android-messages-6c275',
  keyFilename: path.join(__dirname, './keyfile.json')
};

const storage = require('@google-cloud/storage')(config);
const bucket = storage.bucket('android-messages-6c275.appspot.com');

import type {Conversation, Recipient} from './types';

type FirebaseUser = {
  uid: string
};

require('./app.scss');

const isDebug = false;
// const selfConversation = 184;
const selfConversation = 0;
const THREE_MINUTES_MILLIS = 180000;
const TOTAL_CONVERSATIONS_TO_LOAD = 20;

export default class App extends Component {
  static propTypes = {
    children: PropTypes.node,
    loggedIn: PropTypes.bool
  };

  // The input responsible for searching
  searchInput: Object;
  _connected: boolean;

  state: {
    loggedIn: boolean,
    collectPassword: boolean,
    conversations: Array<Conversation>,
    user: Object,
    ownPhoneNumber: number,
    selectedConversationId?: number | null,
    optionsVisible: boolean,
    password: string,
    searchMessage: string
  };

  initialState = {
    loggedIn: firebase.auth().currentUser !== null,
    collectPassword: false,
    conversations: [],
    user: {},
    ownPhoneNumber: -1,
    selectedConversationId: null,
    optionsVisible: false,
    password: localStorage.getItem('password'),
    searchMessage: ''
  };

  constructor (props: Object) {
    super(props);
    this._connected = null;
    this.state = this.initialState;

    // Keyboard shortcuts
    window.document.addEventListener('keydown', (e) => {
      // Scroll through conversations on ALT + arrowUp/arrowDown
      if (e.altKey) {
        const { conversations, selectedConversationId } = this.state;
        if (!conversations.length) {
          return;
        }
        const selectedConversationIndex = conversations.findIndex(c => c.id === selectedConversationId);
        switch (e.key) {
          case 'ArrowDown':
            e.preventDefault();
            let nextConversation;
            if (selectedConversationIndex === -1) {
              nextConversation = conversations[0];
            } else {
              nextConversation = conversations[selectedConversationIndex + 1];
            }
            if (nextConversation) {
              this.setState({ selectedConversationId: nextConversation.id });
            }
            break;
          case 'ArrowUp':
            if (!selectedConversationIndex) {
              return;
            }
            e.preventDefault();
            const previousConversation = conversations[selectedConversationIndex - 1];
            if (previousConversation) {
              this.setState({ selectedConversationId: previousConversation.id });
            }
            break;
        }
      }

      // Focus the search bar on CMD + k
      if (this.searchInput) {
        if (e.metaKey && e.key === 'k') {
          this.searchInput.focus();
        }
      }
    });

    window.document.addEventListener('keyup', () => {

      // If everything is removed from the search input, refresh
      if (this.searchInput && !this.searchInput.value &&
          document.activeElement === this.searchInput) {
        this.getAllConversations();
        this.setState({searchMessage: ''});
      }
    });
  }

  render () {
    let user = firebase.auth().currentUser;
    let email = localStorage.getItem('email') || user && user.email;
    let password = localStorage.getItem('password');

    if (email && password && !this.state.loggedIn) {
      this.login(email, password);
    } else if (!email && !password) {
      return <Login
        login={this.login.bind(this)}
        loginWithGoogle={this.loginWithGoogle.bind(this)}
      />;
    } else if (!password) {
      return <Password
        setPassword={this.setPassword.bind(this)}
      />;
    }

    const selectedConversation = Number.isInteger(this.state.selectedConversationId)
      ? _.find(this.state.conversations, ['id', this.state.selectedConversationId])
      : null;
    const selectedConversationId = this.state.selectedConversationId;
    const selectedConversationName = selectedConversation && selectedConversation.name || null;
    const selectedConversationRecipients = selectedConversation && selectedConversation.recipients || {};

    return (
      <div className="app-container">
        <ConversationsContainer
          conversations={this.state.conversations}
          search={this.search.bind(this)}
          selectedConversationId={this.state.selectedConversationId}
          setSelected={this.setSelected.bind(this)}
          searchInputRef={(input) => { this.searchInput = input; }}
        />
        <MessagesContainer
          changeRecipientColor={this.changeRecipientColor.bind(this)}
          changeConversationName={this.changeConversationName.bind(this)}
          fetchMmsImage={this.fetchMmsImage.bind(this)}
          optionsVisible={this.state.optionsVisible}
          conversationId={selectedConversationId}
          conversationName={selectedConversationName}
          conversationsAreLoading={!this.state.searchMessage && this.state.conversations.length === 0}
          searchMessage={this.state.searchMessage}
          recipients={selectedConversationRecipients}
          ownPhoneNumber={this.state.ownPhoneNumber}
          password={this.state.password}
          userId={this.state.user.uid}
        />
      </div>
    );
  }

  getConversations (limit?: number): Promise<void> {
    const ref = isDebug
      ? `${`users/${this.state.user.uid}`}/conversations/${selfConversation}`
      : `${`users/${this.state.user.uid}`}/conversations`;
    const startConversations = Date.now();
    return firebase.database()
      .ref(ref)
      .orderByChild('lastMessageTimestamp')
      .limitToLast(limit || TOTAL_CONVERSATIONS_TO_LOAD)
      .once('value')
      .then(snapshot => {
        console.debug(`Loaded ${Object.keys(snapshot.val()).length} conversations in ` +
          `${Date.now() - startConversations} ms`);
        return snapshot.val();
      });
  }

  onReconnect (snapshot: Object) {
    const connected = snapshot.val();
    console.debug('Connection status changed -- connected:', connected);
    console.debug('Was connected:', this._connected);

    // Reset if we've disconnected for some reason
    if (connected && this._connected === false) {
      console.debug('Reloading');

      const currentUser = firebase.auth().currentUser || {};
      const newState = {
        ...this.initialState,
        loggedIn: false,
        user: currentUser
      };
      this.setState(newState);
      return;
    }

    this._connected = connected;
  }

  login (email: string, password: string) {
    return firebase.auth()
      .signInWithEmailAndPassword(email, password)
      .then(this.handleLoginSuccess.bind(this))
      .then(() => {
        this.setState({password});
        localStorage.setItem('email', email);
        localStorage.setItem('password', password);
      });
  }

  loginWithGoogle () {
    return googleSignIn()
      .then((user) => {
        // Build Firebase credential with the Google ID token.
        const credential = firebase.auth.GoogleAuthProvider.credential(user.idToken);
        // Sign in with credential from the Google user.
        return firebase.auth().signInWithCredential(credential)
          .then((user) => {
            this.handleLoginSuccess(user);
          })
          .catch((err) => {
            console.error('Failed Firebase auth', err);
          });
      });
  }

  setPassword (password: string) {
    localStorage.setItem('password', password);
    this.setState({password});
    this.handleLoginSuccess(firebase.auth().currentUser);
  }

  handleLoginSuccess (user: FirebaseUser): void {
    console.debug('Successful login', user);
    this._connected = true;
    this.setState({
      loggedIn: true,
      user: user
    });
    let userDatabaseKey = 'users/' + user.uid;

    const boundOnReconnect = this.onReconnect.bind(this);
    firebase.database().ref('.info/connected').off();
    firebase.database().ref('.info/connected').on('value', boundOnReconnect);

    const getOwnPhoneNumberAndListen = (snapshot) => {
      console.debug(`Own phone number: ${snapshot.val()}`);
      this.setState({
        ownPhoneNumber: parseInt(snapshot.val())
      });
      this.getAllConversations();
      this.listenForConversationChange();
    };

    const ownPhoneNumberRef = firebase.database().ref(`${userDatabaseKey}/recipients/self`);
    ownPhoneNumberRef.off();
    ownPhoneNumberRef.once('value').then(getOwnPhoneNumberAndListen);
  }

  getAllConversations () {
    firebase.database()
      .ref(`users/${this.state.user.uid}/conversations`)
      .orderByChild('lastMessageTimestamp')
      .limitToLast(TOTAL_CONVERSATIONS_TO_LOAD)
      .once('value', (snapshot) => {
        return this.normalizeConversations(snapshot.val())
          .then((conversations) => {
            this.setState({ conversations });
            this.listenForConversationAdd();
          });
      });
  }

  normalizeConversations (input: Array<Conversation>) {
    const conversations: Array<Conversation> = _.orderBy(input, 'lastMessageTimestamp', 'desc');
    const recipients = new Set();
    conversations.forEach((conversation) => {
      Object.keys(conversation.recipients).forEach(r => recipients.add(r));
    });

    return this.getRecipients([...recipients])
      .then((inflatedRecipients) => {
        conversations.forEach((conversation) => {
          // Append recipient info
          Object.keys(conversation.recipients).forEach((phoneNumber) => {
            conversation.recipients[phoneNumber] = inflatedRecipients[phoneNumber] || { phoneNumber };
          });
          const { ownPhoneNumber } = this.state;
          if (Object.keys(conversation.recipients).length === 1 &&
            conversation.recipients[ownPhoneNumber]) {
            return;
          }
          delete conversation.recipients[ownPhoneNumber];

          // Decrypt message content
          conversation.lastMessageContent = decrypt(conversation.lastMessageContent, this.state.password);
        });
        return conversations;
      });
  }

  listenForConversationAdd () {
    firebase.database()
      .ref(`users/${this.state.user.uid}/conversations`)
      .orderByChild('lastMessageTimestamp')
      .limitToLast(1)
      .on('child_added', (snapshot) => {
        const newConversation: Conversation = snapshot.val();
        if (_.some(this.state.conversations, c => c.id === newConversation.id)) {
          return;
        }

        return this.normalizeConversations([newConversation])
          .then((conversation) => {
            this.notify(conversation[0]);
            const conversations = conversation.concat(this.state.conversations);
            this.setState({ conversations });
          });

        // return this.getRecipients(Object.keys(newConversation.recipients))
        //   .then((inflatedRecipients) => {
        //     Object.keys(newConversation.recipients).forEach((phoneNumber) => {
        //       newConversation.recipients[phoneNumber] = inflatedRecipients[phoneNumber];
        //     });
        //     const conversations = [newConversation].concat(this.state.conversations);
        //     this.setState({ conversations });
        //   });
      });
  }

  listenForConversationChange () {
    const conversationsRef = `users/${this.state.user.uid}/conversations`;
    firebase.database()
      .ref(conversationsRef)
      .off('child_changed');

    firebase.database()
      .ref(conversationsRef)
      .on('child_changed', _.debounce((snapshot) => {
        const conversation: Conversation = snapshot.val();
        const oldConversations = this.state.conversations.slice();
        const oldConversation = _.find(oldConversations, {id: conversation.id});
        conversation.recipients = oldConversation.recipients;
        _.remove(oldConversations, {id: conversation.id});

        return this.normalizeConversations([conversation])
          .then((normalizedConversation) => {
            const conversations = _.orderBy(
              normalizedConversation.concat(oldConversations),
              'lastMessageTimestamp',
              'desc');
            this.setState({ conversations });
            this.notify(normalizedConversation[0]);
          });
      }, 1000));
  }

  notify (conversation: Conversation) {
    if (conversation.lastMessageSender !== this.state.ownPhoneNumber &&
      (Date.now() - conversation.lastMessageTimestamp) < THREE_MINUTES_MILLIS) {
      this.getRecipients(Object.keys(conversation.recipients))
        .then((recipientMap) => {
          const sender = recipientMap[conversation.lastMessageSender];
          const name = conversation.name || sender.name || sender.phoneNumber;
          const otherRecipients = _.values(recipientMap)
            .filter(r => r.phoneNumber !== sender.phoneNumber)
            .map(r => r.name || r.phoneNumber);
          let senderString = name;
          if (otherRecipients.length > 1) {
            senderString += `, ${otherRecipients.join(',')}`;
          }
          const message = decrypt(conversation.lastMessageContent, this.state.password);
          new window.Notification(`New message from ${senderString}`, {body: message});
        });
    }
  }

  getRecipients (phoneNumbers: Array<string>): Promise<Object> {
    let recipientMap = {};
    return Promise.all(
      phoneNumbers
        .map((phoneNumber) => {
          const cachedRecipient = sessionStorage.getItem(phoneNumber);
          if (cachedRecipient) {
            const recipient = JSON.parse(cachedRecipient);
            recipientMap[phoneNumber] = recipient;
            return Promise.resolve(recipient);
          }

          return firebase.database()
            .ref(`users/${this.state.user.uid}/recipients/${phoneNumber}`)
            .once('value')
            .then((snapshot) => {
              const name = snapshot.val().name || phoneNumber;

              // Actual photo data is stored in Firebase storage
              return this.fetchRecipientPhoto(phoneNumber)
                .then((photo) => {
                  let color = snapshot.val().color;
                  if (!color) {
                    // Set this recipient's color in the database
                    color = getRandomColor();
                    firebase.database().ref(`users/${this.state.user.uid}/recipients/${phoneNumber}/color`).set(color);
                  }
                  const recipient = {
                    name: name,
                    phoneNumber: phoneNumber,
                    photoUri: photo,
                    color: color
                  };
                  sessionStorage.setItem(phoneNumber, JSON.stringify(recipient));
                  recipientMap[phoneNumber] = recipient;
                  return recipient;
                });
            })
            .catch((err) => {
              console.error(`Errored retrieving recipient: ${phoneNumber}`, err);
              const recipient = {
                name: phoneNumber,
                phoneNumber: phoneNumber,
                photoUri: '',
                color: getRandomColor()
              };
              recipientMap[phoneNumber] = recipient;
              return recipient;
            });
        })).then(() => recipientMap);
  }

  search (searchString: string) {
    // Show that we're loading matches, deselect any conversations
    this.setState({
      selectedConversationId: null
    });

    // Load the default list
    if (!searchString) {
      return this.getAllConversations();
    }

    const startSearch = Date.now();
    const lowerCasedSearchString = searchString.toLowerCase();

    // TODO: search by conversation name too
    return Promise.all([
      this.getConversations(100),
      this.getAllRecipients()
    ])
      .then((response) => {
        const conversations = _.values(response[0]);
        const recipients = response[1];
        const conversationMatches = conversations
          .filter((c) => {
            return c.name && c.name.toLowerCase().indexOf(lowerCasedSearchString) !== -1;
          })
          .map(c => c.id);
        const recipientMatches = recipients
          .filter((recipient) => {
            const isPhoneNumberMatch = recipient.phoneNumber &&
              recipient.phoneNumber.toString().indexOf(lowerCasedSearchString) !== -1;
            const isNameMatch = recipient.name &&
              recipient.name.toLowerCase().indexOf(lowerCasedSearchString) !== -1;
            const isSelf = recipient.phoneNumber === this.state.ownPhoneNumber;
            return isPhoneNumberMatch || isNameMatch && !isSelf;
          });

        const conversationIds = _.uniq(
          _.flatten(recipientMatches.map(match => Object.keys(match.conversations)))
            .concat(conversationMatches)
        );
        return this.getConversationsByIds(conversationIds)
          .then((rawConversations) => {
            console.debug(`Found ${rawConversations.length} matches in ${Date.now() - startSearch} ms`);
            return this.normalizeConversations(rawConversations)
              .then((normalizedConversations) => {
                // Put the direct message (if found) first
                let conversations = normalizedConversations;
                let direct;
                normalizedConversations.some((c, i) => {
                  if (Object.keys(c.recipients).length === 1) {
                    direct = normalizedConversations.splice(i, 1);
                    return true;
                  }
                });

                if (direct) {
                  conversations.unshift(...direct);
                }

                let searchMessage = '';
                if (conversations.length === 0) {
                  searchMessage = 'No matching conversations found.';
                }
                this.setState({
                  conversations,
                  searchMessage
                });
              });
          });
      });
  }

  getAllRecipients (): Promise<Array<Recipient>> {
    return firebase.database()
      .ref(`${`users/${this.state.user.uid}`}/recipients`)
      .once('value')
      .then((snapshot) => {
        return _.values(snapshot.val());
      });
  }

  getRecipientInfoForConversations (conversations: Array<Conversation>): Promise<Array<Conversation>> {
    // Fetch recipient info for all our loaded conversations (that we haven't already fetched)
    const conversationsToUpdate = conversations.filter(conversation => !conversation.recipientInfo);
    const allRecipients = _.flatten(conversationsToUpdate.map(conversation => Object.keys(conversation.recipients)));
    const uniqueRecipients = _.uniq(allRecipients);

    return this.getRecipients(uniqueRecipients)
      .then((recipientMap) => {
        // Add this metadata to relevant conversations
        conversationsToUpdate.forEach((conversation) => {
          const recipientInfo = conversation.recipientInfo || {};
          const recipients = Object.keys(conversation.recipients);

          // Special case a conversation with ourself (attach ourself as a recipient in this case)
          if (recipients.length === 1 && parseInt(recipients[0]) === this.state.ownPhoneNumber) {
            recipientInfo[recipients[0]] = recipientMap[recipients[0]];
          }

          recipients
            .filter(phoneNumber => parseInt(phoneNumber) !== this.state.ownPhoneNumber)
            .forEach((recipient) => {
              recipientInfo[recipient] = recipientMap[recipient];
            });
          conversation.recipientInfo = recipientInfo;
        });

        return conversations;
      });
  }

  getConversationsByIds (conversationIds: Array<number>): Promise<Array<Conversation>> {
    return Promise.all(conversationIds.map((conversationId) => {
      return firebase.database()
        .ref(`${`users/${this.state.user.uid}`}/conversations/${conversationId}`)
        .once('value')
        .then(snapshot => snapshot.val());
    }));
  }

  setSelected (conversationId: number) {
    if (conversationId !== this.state.selectedConversationId) {
      this.setState({
        selectedConversationId: conversationId
      });
    }
  }

  changeRecipientColor (phoneNumber: number, color: string) {
    sessionStorage.removeItem(phoneNumber.toString());
    return firebase.database().ref(`users/${this.state.user.uid}/recipients/${phoneNumber}/color`).set(color)
      .then(() => {
        this.getAllConversations();
      });
  }

  changeConversationName (conversationId: number, name: string) {
    return firebase.database().ref(`users/${this.state.user.uid}/conversations/${conversationId}/name`).set(name);
  }

  fetchRecipientPhoto (phoneNumber: string): Promise<string> {
    const photoStorageRef = bucket.file(`user/${this.state.user.uid}/photo-uris/${phoneNumber}`);
    return new Promise((resolve, reject) => {
      photoStorageRef.download((err, data) => {
        if (err) return reject(err);
        resolve(data);
      });
    })
      .then((response) => {
        return decrypt(response.toString(), this.state.password);
      })
      .catch((err) => {
        console.warn('Error requesting to Firebase storage', err);
      });
  }

  fetchMmsImage (attachmentId: string): Promise<string> {
    const imageStorageRef = bucket.file(`user/${this.state.user.uid}/mms-images/${attachmentId}`);
    return new Promise((resolve, reject) => {
      imageStorageRef.download((err, data) => {
        if (err) return reject(err);
        return resolve(data);
      });
    })
      .then((response) => {
        return decrypt(response.toString(), this.state.password);
      })
      .catch((err) => {
        console.warn('Error requesting to Firebase storage', err);
        return '';
      });
  }
}
