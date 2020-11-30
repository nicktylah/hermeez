// @flow
import React, {Component, PropTypes} from 'react';
import _ from 'lodash';
import moment from 'moment';
import RecipientPhotoContainer from './recipientPhotoContainer';
import RecipientContainer from './recipientContainer';
import DateContainer from './dateContainer';
import MessageContainer from './messageContainer';

require('./conversation.scss');

export default class Conversation extends Component {
  static propTypes = {
    identifier: PropTypes.number,
    lastMessageTimestamp: PropTypes.number,
    lastMessageContent: PropTypes.string,
    lastMessageAttachment: PropTypes.string,
    lastMessageAttachmentContentType: PropTypes.string,
    lastMessageSender: PropTypes.string,
    name: PropTypes.string,
    recipients: PropTypes.object,
    setSelected: PropTypes.func,
    selected: PropTypes.bool
  };

  shouldComponentUpdate (nextProps: Object, nextState: Object) {
    // This is due to some weirdness with react-set props? Like "key?" I dunno.
    const propsAreUnequal = [
      'identifier',
      'lastMessageAttachment',
      'lastMessageAttachmentContentType',
      'lastMessageContent',
      'lastMessageSender',
      'lastMessageTimestamp',
      'name',
      'recipients',
      'selected'
    ].some(key => !_.isEqual(this.props[key], nextProps[key]));
    return propsAreUnequal || !_.isEqual(this.state, nextState);
  }

  render () {
    const {
      lastMessageAttachment,
      lastMessageContent,
      lastMessageSender,
      lastMessageTimestamp,
      name,
      identifier,
      recipients,
      selected,
      setSelected
    } = this.props;

    let text = lastMessageContent;
    if (lastMessageContent) {
      text = lastMessageContent;
    } else {
      text = lastMessageAttachment && 'Image' || '';
    }

    const now = moment();
    let formattedDate;
    if (lastMessageTimestamp) {
      const ts = moment(lastMessageTimestamp);
      formattedDate = now.diff(ts) <= 86400000 ? ts.format('H:mm') : ts.format('M/D/YY');
    } else {
      formattedDate = 'Unknown';
    }

    return (
      <div
        className={`conversation-container ${selected && 'selected' || ''}`}
        onClick={setSelected}
        data-conversation-id={identifier}>
        <li className="conversation">
          <RecipientPhotoContainer
            recipients={recipients}
            lastMessageSender={lastMessageSender}
          />
          <RecipientContainer recipients={name || _.values(recipients).map((r => r.name)).join(', ')} />
          <DateContainer date={formattedDate} />
          <br />
          <MessageContainer message={text} />
        </li>
      </div>
    );
  }

}
