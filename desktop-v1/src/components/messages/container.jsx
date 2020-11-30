// @flow
import React, {Component, PropTypes} from 'react';
import _ from 'lodash';
import Header from './header';
import Content from './content';
import InputContainer from './input';
import decrypt from '../../lib/encyption';

import type {Message as MessageType} from '../types';

require('./container.scss');

const fcmServerAddress = 'https://hermeez.co/fcm/send';
const INITIAL_MESSAGE_COUNT = 30;

export default class MessagesContainer extends Component {
  static propTypes = {
    fetchMmsImage: PropTypes.func,
    changeRecipientColor: PropTypes.func,
    changeConversationName: PropTypes.func,
    children: PropTypes.node,
    conversationsAreLoading: PropTypes.bool,
    showOptions: PropTypes.func,
    optionsVisible: PropTypes.bool,
    conversationId: PropTypes.number,
    recipients: PropTypes.object,
    conversationName: PropTypes.string,
    ownPhoneNumber: PropTypes.number,
    password: PropTypes.string,
    searchMessage: PropTypes.string,
    userId: PropTypes.string
  };

  messagesRef: null;
  boundListenForMessageAdd: (snapshot: Object) => void;

  state: {
    messages: Array<MessageType>,
    isLoading: boolean
  };

  decryptAndAddMmsData(message: MessageType): Promise<MessageType> {
    const decrypted = decrypt(message.content, this.props.password);
    if (decrypted) {
      message.content = decrypted;
    }
    if (message.attachment) {
      return this.props.fetchMmsImage(message.attachment)
        .then((base64Image) => {
          message.attachment = base64Image;
          return message;
        });
    } else {
      return Promise.resolve(message);
    }
  }

  loadMoreMessages() {
    if (!this.messagesRef) {
      console.debug('Cannot load more messages for null messagesRef');
      return;
    }
    const startLoadMoreMessages = Date.now();
    const { messages } = this.state;
    const earliestMessageTimestamp = messages.length && messages[0].timestamp;
    return this.messagesRef
      .orderByChild('timestamp')
      .endAt(earliestMessageTimestamp - 1)
      .limitToLast(INITIAL_MESSAGE_COUNT)
      .once('value')
      .then((snapshot) => {
        const newMessagesRaw = _.values(snapshot.val());
        return Promise.all(newMessagesRaw.map(m => this.decryptAndAddMmsData(m)))
          .then((newMessages) => {
            newMessages = _.orderBy(newMessages, 'timestamp', 'asc');
            console.debug(`loaded ${newMessages.length} older messages in ${Date.now() - startLoadMoreMessages} ms`);
            const allMessages = newMessages.concat(messages);
            sessionStorage.setItem(this.props.conversationId.toString(), JSON.stringify(allMessages));
            this.setState({ messages: allMessages });
          });
      });
  }

  componentWillReceiveProps(nextProps: Object) {
    if (nextProps.userId && nextProps.conversationId &&
      nextProps.conversationId !== this.props.conversationId) {
      this.setState({
        messages: [],
        isLoading: true
      });

      // Remove previous listener
      if (this.messagesRef) {
        this.messagesRef.off('child_added', this.boundListenForMessageAdd);
      }

      this.messagesRef = window.firebase.database()
        .ref(`${`users/${nextProps.userId}`}/messages/${nextProps.conversationId}`);

      // Get messages for this conversation
      this.getMessages(nextProps.conversationId);
    }
  }

  getMessages(conversationId: number) {
    const cacheString = sessionStorage.getItem(conversationId.toString()) || '[]';
    const cachedMessages = JSON.parse(cacheString);
    this.setState({
      messages: cachedMessages
    });

    this.messagesRef
      .orderByChild('timestamp')
      .limitToLast(INITIAL_MESSAGE_COUNT)
      .once('value')
      .then((snapshot) => {
        const start = Date.now();
        const messages = _.values(snapshot.val());
        const lastCachedTimestamp = cachedMessages.length && cachedMessages[cachedMessages.length - 1].timestamp || 0;
        const newMessages = messages.filter(m => m.timestamp > lastCachedTimestamp);
        return Promise.all(newMessages.map(m => this.decryptAndAddMmsData(m)))
          .then((messages) => {
            console.debug(`Took ${Date.now() - start} ms to get new messages`);
            const allMessages = _.orderBy(cachedMessages.concat(messages), 'timestamp', 'asc');
            sessionStorage.setItem(conversationId.toString(), JSON.stringify(allMessages));
            this.setState({
              messages: allMessages,
              isLoading: false
            });

            // Setup a listener for new messages in this conversation
            this.messagesRef
              .orderByChild('timestamp')
              .limitToLast(1)
              .on('child_added', this.boundListenForMessageAdd);
          });
      });
  }

  listenForMessageAdd(snapshot: Object): void {
    const message: MessageType = snapshot.val();
    if (_.some(this.state.messages, m => m.id === message.id)) {
      return;
    }
    return this.decryptAndAddMmsData(message)
      .then((message: MessageType) => {
        const messages = this.state.messages
          .filter(m => !m.inFlight)
          .concat([message]);
        this.setState({ messages });
      });
  }

  shouldComponentUpdate(nextProps: Object, nextState: Object) {
    return !_.isEqual(this.props, nextProps) || !_.isEqual(this.state, nextState);
  }

  constructor(props: Object) {
    super(props);
    this.state = {
      messages: [],
      isLoading: true
    };
    this.boundListenForMessageAdd = this.listenForMessageAdd.bind(this);
  }

  sendMessage(recipientIds: Array<string>, e: Object) {
    // Enter was pressed with alt key, do not send
    if (e.keyCode === 13 && e.altKey) {
      return;
    }
    if (e.key === 'Enter' && !e.altKey) {
      e.preventDefault();

      // This is because React hates it if you access properties on "e.target" itself
      const target = e.target;
      const message = target.value;

      // Push this in-flight message onto the messages object. Make it an enormous number so it appears last
      const messages = this.state.messages.slice();
      messages.push({
        id: Number.MAX_SAFE_INTEGER,
        // attachment: '',
        // attachmentContentType: '',
        content: message,
        sender: this.props.ownPhoneNumber,
        timestamp: Date.now(),
        inFlight: true
      });
      this.setState({
        messages: messages
      });
      target.value = '';

      let toSendTo = recipientIds
        .filter(id => id !== this.props.ownPhoneNumber.toString()).join(',');
      if (recipientIds.length === 1 &&
        recipientIds[0] === this.props.ownPhoneNumber.toString()) {
        toSendTo = this.props.ownPhoneNumber.toString()
      }
      const req = new Request(fcmServerAddress, {
        method: 'POST',
        mode: 'no-cors',
        body: JSON.stringify({
          to: this.props.userId,
          content: message,
          attachment: '',
          attachmentContentType: '',
          recipientIds: toSendTo
        })
      });

      return fetch(req)
        .catch((err) => {
          console.error(err);
          this.setState({
            messages: this.state.messages.slice(0, this.state.messages.length - 2)
          });
        })
        .then(() => {
          this.refs.content._div.scrollTop = this.refs.content._div.scrollHeight;
        });
    }
  }

  render() {
    const {
      changeRecipientColor,
      changeConversationName,
      conversationsAreLoading,
      showOptions,
      optionsVisible,
      conversationId,
      recipients,
      conversationName,
      ownPhoneNumber,
      searchMessage,
      userId
    } = this.props;
    const messages = conversationId ? this.state.messages : [];
    return (
      <div className="messages-container">
        <Header
          changeRecipientColor={changeRecipientColor}
          changeConversationName={changeConversationName}
          conversationId={conversationId}
          showOptions={showOptions}
          optionsVisible={optionsVisible}
          conversationName={conversationName}
          recipients={_.values(recipients)}
        />
        <Content
          ref="content"
          conversationId={conversationId}
          conversationsAreLoading={conversationsAreLoading}
          isLoading={this.state.isLoading}
          loadMoreMessages={this.loadMoreMessages.bind(this)}
          messages={messages}
          ownPhoneNumber={ownPhoneNumber}
          recipients={recipients}
          searchMessage={searchMessage}
          userId={userId}
        />
        {conversationId && <InputContainer
          sendMessage={this.sendMessage.bind(this)}
          recipientIds={Object.keys(recipients)}
        />}
      </div>
    );
  }

}
