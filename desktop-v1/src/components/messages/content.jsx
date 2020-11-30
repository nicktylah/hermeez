// @flow
import React, {Component, PropTypes} from 'react';
import _ from 'lodash';
import Loading from 'react-loading';
import Message from './message';

const MAX_STREAK_DELAY_S = 30;

export default class Content extends Component {
  static propTypes = {
    conversationId: PropTypes.number,
    conversationsAreLoading: PropTypes.bool,
    isLoading: PropTypes.bool,
    loadMoreMessages: PropTypes.func,
    messages: PropTypes.array,
    ownPhoneNumber: PropTypes.number,
    recipients: PropTypes.object,
    searchMessage: PropTypes.string,
    userId: PropTypes.string,
    setConversationName: PropTypes.func
  };

  _div: Object;
  _debouncedHandleScroll: (e: Object) => void;
  _messageRefs: Array<Object>;

  constructor() {
    super();
    this._debouncedHandleScroll = _.debounce(this.handleScroll.bind(this), 1000);
    this._messageRefs = [];
  }

  handleScroll(e: Object) {
    const target = e.target;
    const { messages } = this.props;
    if (messages && messages.length && target.scrollTop === 0) {
      return this.props.loadMoreMessages();
    }
  }

  componentDidUpdate() {
    if (this._div) {
      this._div.scrollTop = this._div.scrollHeight;
    }
  }

  render() {
    const {
      recipients,
      messages,
      ownPhoneNumber,
      conversationId,
      isLoading,
      conversationsAreLoading,
      searchMessage
    } = this.props;

    // If MMS, we'll display the message sender's info
    const isMms = Object.keys(recipients).length > 1;
    const inflatedMessages = messages.map((message, i) => {
      let streakClass = '';
      const isSelf = message.sender === ownPhoneNumber;

      // If this is a streak of messages from the same sender, we'll squish them together
      const nextMessage = messages[i + 1];
      const isInStreak = nextMessage && nextMessage.sender === message.sender &&
        nextMessage && nextMessage.timestamp - message.timestamp > MAX_STREAK_DELAY_S;
      if (isInStreak) {
        streakClass = 'streak';
      }
      let recipientInfo = {};
      if (isMms && !isSelf && recipients[message.sender]) {
        recipientInfo = recipients[message.sender];
      }
      let color = null;
      if (!isSelf) {
        color = recipients[message.sender] &&
          recipients[message.sender].color || 'gray';
      }
      return <Message
        key={i}
        isSelf={isSelf}
        message={message}
        recipientInfo={recipientInfo}
        streak={streakClass}
        color={color}
        ref={(r) => this._messageRefs.push(r)}
      />;
    });

    if ((conversationId && isLoading) || conversationsAreLoading) {
      return (
        <div className="content">
          <div className="loading">
            <Loading type='spin' color='#2a83f1' className="loading"/>
          </div>
        </div>
      );
    }

    if (searchMessage) {
      return (
          <div className="content">
            <div className="search-message">{searchMessage}</div>
          </div>
      );
    }

    return (
        <div
          ref={(ref) => {
            if (ref) {
              this._div = ref;
              this._div.removeEventListener('scroll', this._debouncedHandleScroll);
              this._div.addEventListener('scroll', this._debouncedHandleScroll);
            }
          }}
          className="content">
          {inflatedMessages}
        </div>
    );
  }

}
