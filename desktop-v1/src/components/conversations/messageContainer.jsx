// @flow
import React, {Component, PropTypes} from 'react';

export default class MessageContainer extends Component {
  static propTypes = {
    message: PropTypes.string
  };

  shouldComponentUpdate(nextProps) {
    return nextProps.message !== this.props.message;
  }

  constructor(props) {
    super(props);
  }

  render() {
    const text = this.props.message === 'Image'
      ? <i>{this.props.message}</i>
      : this.props.message;

    return (
      <div className="message-container">
        <span className="message">{text}</span>
      </div>
    );
  }

}
