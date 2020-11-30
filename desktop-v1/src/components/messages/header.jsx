// @flow
import React, {Component, PropTypes} from 'react';
import RecipientOptions from './recipientOptions';

export default class Header extends Component {
  static propTypes = {
    changeRecipientColor: PropTypes.func,
    changeConversationName: PropTypes.func,
    conversationId: PropTypes.number,
    showOptions: PropTypes.func,
    optionsVisible: PropTypes.bool,
    conversationName: PropTypes.string,
    recipients: PropTypes.array
  };

  state: {
    isEditing: boolean
  };

  constructor(props: Object) {
    super(props);
    this.state = {
      isEditing: false
    };
  }

  onDetailsClick() {
    this.setState({ isEditing: !this.state.isEditing });
  }

  onNameInputKeyPress(e: Object): void {
    if (e.key === 'Enter') {
      this.props.changeConversationName(this.props.conversationId, e.target.value);
      this.setState({ isEditing: false });
    }
  }

  componentWillReceiveProps() {
    this.setState({ isEditing: false });
  }

  render() {
    const {
      conversationId,
      conversationName,
      recipients,
      changeRecipientColor
    } = this.props;

    const recipientNames = recipients.map((r, i) => {
      const text = r.name || r.phoneNumber;
      return [
        <div className="recipient" key={i} style={{background: r.color}}>
          {text}
          <RecipientOptions changeRecipientColor={changeRecipientColor} recipient={r} />
        </div>
      ];
    });

    let headerText = conversationName;
    if (!conversationName) {
      headerText = recipientNames;
    }

    if (this.state.isEditing) {
      headerText = <input defaultValue={conversationName} placeholder="Conversation Name" onKeyPress={this.onNameInputKeyPress.bind(this)}/>;
    }

    return (
      <div className="header">
        <div className="header-top">
          <div className="conversation-name-container">
            {headerText}
          </div>
          {conversationId && <div className="details" onClick={this.onDetailsClick.bind(this)}>Details</div>}
        </div>
        {this.state.isEditing && <div className="name-container">
          {recipientNames}
        </div>}
      </div>
    );
  }

}
