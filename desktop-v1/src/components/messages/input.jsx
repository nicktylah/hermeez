// @flow
import React, {Component, PropTypes} from 'react';

export default class InputContainer extends Component {
  static propTypes = {
    recipientIds: PropTypes.array,
    sendMessage: PropTypes.func
  };

  constructor(props) {
    super(props);
  }

  render() {
    return (
      <div className="input-container">
        <textarea className="input" onKeyPress={this.props.sendMessage.bind(null, this.props.recipientIds)}/>
      </div>
    );
  }

}
