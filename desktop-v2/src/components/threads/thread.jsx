// @flow
import React, { Component, PropTypes } from 'react';
import { format, distanceInWordsToNow, differenceInDays } from 'date-fns';
import classnames from 'classnames';
import { isEqual } from 'lodash';

import type { Thread as TThread } from '../../types';

import style from './threads.scss';

type Props = {
  selected: boolean,
  ...TThread
};

export default class Thread extends React.Component<Props> {
  shouldComponentUpdate(nextProps: Props) {
    return !isEqual(this.props, nextProps);
  }

  render() {
    let { attachment, body, contacts, date, sender, threadId } = this.props;

    contacts = contacts || [];

    let text = '';
    if (body) {
      text = body;
    } else if (attachment) {
      text = <i>Image</i>;
    }

    let timeString = '';
    if (differenceInDays(new Date(), date) >= 1) {
      timeString = format(date, 'M/D/YY');
    } else {
      timeString = `${distanceInWordsToNow(date)} ago`.replace('about', '');
    }

    const threadName = contacts
      .map(c => c.displayName || c.phoneNumber)
      .join(', ');

    const shouldLayer = contacts.length > 1;
    const photos = contacts.slice(0, 2).map(c => {
      const contactStyle = classnames(style.contactAvatar, {
        [style.layered]: shouldLayer
      });

      if (c.photo) {
        return (
          <img
            key={c.phoneNumber}
            className={contactStyle}
            src={`data:image/jpeg;base64,${c.photo}`}
          />
        );
      } else if (c.displayName) {
        const split = c.displayName.split(' ');
        let abbrv = split[0][0];
        if (split.length > 1) {
          abbrv = split[0][0] + split[split.length - 1][0];
        }
        abbrv = abbrv.toUpperCase();
        return (
          <div
            key={c.phoneNumber}
            className={classnames(contactStyle, style.name)}
          >
            {abbrv}
          </div>
        );
      } else {
        return (
          <div
            key={c.phoneNumber}
            className={classnames(contactStyle, style.default)}
          >
            ?
          </div>
        );
      }
    });

    return (
      <li className={style.thread}>
        <div className={style.photosCont}>{photos}</div>
        <div className={style.contactCont}>
          <span className="id" title={threadName}>
            {threadName}
          </span>
        </div>
        <div className={style.dateCont}>
          <span className="date">{timeString}</span>
        </div>
        <br />
        <div className={style.messageCont}>
          <span className="message">{text}</span>
        </div>
      </li>
    );
  }
}
