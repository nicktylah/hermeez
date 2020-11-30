// @flow
import React, {Component, PropTypes} from 'react';
import _ from 'lodash';
import moment from 'moment';
import createAvatar from '../../lib/create-avatar';

export default class Message extends Component {
  static propTypes = {
    message: PropTypes.object,
    isSelf: PropTypes.bool,
    recipientInfo: PropTypes.object,
    streak: PropTypes.string
  };

  constructor(props) {
    super(props);
  }

  shouldComponentUpdate(nextProps) {
    return !_.isEqual(this.props, nextProps);
  }

  componentDidMount() {
    if (this.refs.img) {
      const {img, messageContainer} = this.refs;
      const toAdd = messageContainer.classList && _.values(messageContainer.classList).indexOf('mms') !== -1 ? 55 : 20;
      messageContainer.style.width = `${img.clientWidth + toAdd}px`;
    }
  }

  render() {
    let avatar;
    if (Object.keys(this.props.recipientInfo).length) {
      avatar = createAvatar(this.props.recipientInfo, 0, false);
    }

    const {isSelf, message, color, streak} = this.props;

    const isSelfClass = isSelf ? 'self' : '';
    const isMmsClass = avatar ? 'mms' : '';
    const isInFlightClass = message.inFlight ? 'in-flight' : '';
    const backgroundColorStyle = color && { background: color };
    const src = `data:${message.attachmentContentType};charset=utf-8;base64,${message.attachment}`;
    let img;
    if (message.attachmentContentType) {
      if (message.attachmentContentType.includes('image')) {
        img = <img
          ref="img"
          key="image"
          className="message-image"
          src={src}/>;
      } else if (message.attachmentContentType.includes('video')) {
        img = <video controls>
          <source
            key="video-source"
            type={message.attachmentContentType}
            src={src}/>
        </video>;
      }
    }

    let messageWidthStyle;
    if (img) {
      messageWidthStyle = {width: `${img.clientWidth + 20}px`};
    }

    return (
      <div
        ref="messageContainer"
        className={`message-container ${isSelfClass} ${streak} ${isMmsClass}`}
        data-message-id={message.id}
        style={messageWidthStyle}>
        {avatar && <div className="message-sender">{avatar}</div>}
        <div
          className={`message ${isSelfClass} ${isMmsClass} ${isInFlightClass}`}
          style={backgroundColorStyle}>
          {message.attachment &&
          [
            img,
            <br key="br"/>
          ]}
          {message.content}</div>
        {!streak && <span className="timestamp">{moment(message.timestamp).format('MM/DD HH:mm')}</span>}
      </div>
    );
  }

}
