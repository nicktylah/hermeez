// @flow
import React, { Component, PropTypes } from 'react';
import Fade from 'react-reveal/Fade';
import classnames from 'classnames';
import { format, distanceInWordsToNow, differenceInDays } from 'date-fns';

import Reaction from './reaction';

import type {
  Contact,
  Message as TMessage,
  Reaction as TReaction
} from '../../types';

import style from './messages.scss';

type Props = {
  ...TMessage,
  color: ?string,
  contact: Contact,
  isMmsThread: boolean,
  isSelf: boolean,
  isStreak: boolean,
  reactions: TReaction[]
};

const reactionCharacters = {
  'Laughed at': '😂',
  Emphasized: '❗',
  Disliked: '👎',
  Liked: '👍',
  Loved: '😍',
  Questioned: '❓'
};

export default class Message extends React.Component<Props> {
  componentDidMount() {
    if (this.props.attachment) {
      console.debug('This message has an attachment! You should fetch it');
    }
  }

  render() {
    let {
      attachmentUrl,
      body,
      color,
      contact,
      contentType,
      date,
      isMmsThread,
      isSelf,
      isStreak,
      reactions,
      threadId
    } = this.props;

    contentType = contentType || '';

    let reactionsToRender = {};
    if (reactions && reactions.length > 0) {
      reactionsToRender = reactions.reduce((prev, curr) => {
        const byType = prev[curr.type] || { count: 0, names: [] };
        byType.count++;
        byType.names.push(curr.reactorName);
        return {
          ...prev,
          [curr.type]: byType
        };
      }, {});
    }

    let contactAvatar = null;
    if (isMmsThread && !isSelf) {
      if (contact.photo) {
        contactAvatar = (
          <img
            key={contact.phoneNumber}
            className={style.contactAvatar}
            src={`data:image/jpeg;base64,${contact.photo}`}
          />
        );
      } else if (contact.displayName) {
        const split = contact.displayName.split(' ');
        let abbrv = split[0][0];
        if (split.length > 1) {
          abbrv = split[0][0] + split[split.length - 1][0];
        }
        abbrv = abbrv.toUpperCase();
        contactAvatar = (
          <div
            key={contact.phoneNumber}
            className={classnames(style.contactAvatar, style.name)}
          >
            {abbrv}
          </div>
        );
      } else {
        contactAvatar = (
          <div
            key={contact.phoneNumber}
            className={classnames(style.contactAvatar, style.default)}
          >
            ?
          </div>
        );
      }
    }

    let image = null;
    let wideStyle = {};
    if (attachmentUrl) {
      if (contentType.includes('image')) {
        image = (
          <img
            ref="img"
            key="image"
            className={style.messageImage}
            src={attachmentUrl}
          />
        );
      } else if (contentType.includes('video')) {
        image = (
          <video controls>
            <source key="video-source" type={contentType} src={attachmentUrl} />
          </video>
        );
      }

      // Make the background color blob wide enough
      if (image) {
        // $FlowFixMe
        wideStyle = { width: `${image.clientWidth + 20}px` };
      }
    }

    const containerClass = classnames(style.messageCont, wideStyle, {
      [style.self]: isSelf,
      [style.streak]: isStreak,
      [style.mms]: isMmsThread
    });
    const messageClass = classnames(style.message, {
      [style.self]: isSelf,
      [style.mms]: isMmsThread
    });

    return (
      <Fade top duration={500}>
        <div className={containerClass}>
          {isMmsThread && (
            <div className={style.messageSender}>{contactAvatar}</div>
          )}
          <div>
            <div className={messageClass} style={{ background: color }}>
              {image}
              {body}
            </div>
            {Object.keys(reactionsToRender).length > 0 &&
              Object.keys(reactionsToRender).map((reactionType, i) => {
                return (
                  <Reaction
                    key={i}
                    names={reactionsToRender[reactionType].names}
                    isSelf={isSelf}
                    type={reactionCharacters[reactionType]}
                  />
                );
              })}
          </div>
          {!isStreak && (
            <span className={style.timestamp}>
              {format(date, 'MM/DD HH:mm')}
            </span>
          )}
        </div>
      </Fade>
    );
  }
}
