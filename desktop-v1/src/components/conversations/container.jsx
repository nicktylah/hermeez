// @flow
import React, {Component, PropTypes} from 'react';
import Conversation from './conversation';
import Search from './search';

require('./container.scss');

export default class ConversationsContainer extends Component {
  static propTypes = {
    children: PropTypes.node,
    conversations: PropTypes.array,
    search: PropTypes.func,
    searchInputRef: PropTypes.func,
    selectedConversationId: PropTypes.number,
    setSelected: PropTypes.func
  };

  constructor(props: Object) {
    super(props);
  }

  render() {
    const conversations = this.props.conversations.map(function(conversation, i) {
      return <Conversation
        key={i}
        identifier={conversation.id}
        lastMessageTimestamp={conversation.lastMessageTimestamp}
        lastMessageContent={conversation.lastMessageContent}
        lastMessageAttachment={conversation.lastMessageAttachment}
        lastMessageAttachmentContentType={conversation.lastMessageAttachmentContentType}
        lastMessageSender={conversation.lastMessageSender && conversation.lastMessageSender.toString() || ""}
        name={conversation.name}
        recipients={conversation.recipients}
        setSelected={this.props.setSelected.bind(null, conversation.id)}
        selected={this.props.selectedConversationId === conversation.id}
      />;
    }.bind(this));

    return (
      <div className="conversations-container">
        <Search
          search={this.props.search}
          searchInputRef={this.props.searchInputRef}
        />
        <ul className="conversations-list-container">
          {conversations}
        </ul>
      </div>
    );
  }
}
