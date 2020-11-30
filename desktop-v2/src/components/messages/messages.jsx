import * as React from 'react';
import Fade from 'react-reveal/Fade';
import { differenceInSeconds } from 'date-fns';
import { isEqual } from 'lodash';

import Message from './message';

import type { ElementRef } from 'react';
import type { State as MessagesState } from '../../reducers/messages';
import type { Contact } from '../../types';
import { getRandomColor } from '../../lib/colors';

import style from './messages.scss';

const MAX_STREAK_DELAY_S = 300;

export type MessagesProps = {|
  children: React.Node,
  location: Location
|};

export type ConnectedProps = {|
  contacts: { [phoneNumber: number]: Contact },
  messages: MessagesState
|};

type CombinedProps = {
  ...MessagesProps,
  ...ConnectedProps
};

export default class Messages extends React.Component<CombinedProps, void> {
  _content: ElementRef<*>;
  _messagesEnd: ElementRef<*>;

  shouldComponentUpdate(nextProps: CombinedProps) {
    return !isEqual(this.props, nextProps);
  }

  componentDidUpdate() {
    setTimeout(() => {
      this._content.scrollTop = this._content.scrollHeight;
    }, 10);
  }

  render() {
    const { props } = this;

    if (props.messages.error) {
      return <div className={style.content}>Error loading messages.</div>;
    }

    if (props.messages.loading) {
      return <div className={style.content} />;
    }


    const groupedMessages = props.messages.messages.reduce((acc, curr, i) => {
      if (i === 0) {
        acc.push([curr]);
        return acc;
      }

      const prev = acc[acc.length - 1];
      if (curr.sender === prev[prev.length - 1].sender &&
        differenceInSeconds(curr.date, prev[prev.length - 1].date) <
        MAX_STREAK_DELAY_S) {
        acc.pop();
        acc.push(prev.concat(curr));
      } else {
        acc.push([curr])
      }
      return acc;
    }, []);

    const decoratedGroupedMessages = groupedMessages.map((messageGroup, i) => {
      const messages = messageGroup.map((m, j) => {
        m = this.decorateMessage(m, j);
        return (<Message
          key={j}
          attachmentUrl={m.attachmentUrl}
          body={m.body}
          color={m.color}
          contact={m.contact}
          contentType={m.contentType}
          date={m.date}
          isMmsThread={m.isMmsThread}
          isSelf={m.isSelf}
          isStreak={m.isStreak}
          reactions={m.reactions}
          threadId={m.threadId}
        />);
      });

      return (
        <Fade key={i} top duration={500}>
          <div >
            {messages}
          </div>
        </Fade>
      )
    });

    const messages = props.messages.messages.map((m, i) => {
      m = this.decorateMessage(m, i);

      // if (m.isStreak) {
      //   let inStreak = true;
      //   const messagesStreak = [
      //     <Message
      //       key={i}
      //       attachmentUrl={m.attachmentUrl}
      //       body={m.body}
      //       color={m.color}
      //       contact={m.contact}
      //       contentType={m.contentType}
      //       date={m.date}
      //       isMmsThread={m.isMmsThread}
      //       isSelf={m.isSelf}
      //       isStreak={m.isStreak}
      //       reactions={m.reactions}
      //       threadId={m.threadId}
      //     />
      //   ];

      //   while (inStreak) {
      //     console.log('inStreak');
      //     let index = i;
      //     let nextMessage = props.messages.messages[index + 1] || {};
      //     if (
      //       nextMessage.sender === m.sender &&
      //       differenceInSeconds(nextMessage.date, m.date) < MAX_STREAK_DELAY_S
      //     ) {
      //       console.log(`nextMessage is in streak too. Index: ${index}`)
      //       let nextDecoratedMessage = this.decorateMessage(nextMessage, index + 1);
      //       messagesStreak.push(
      //         <Message
      //           key={i}
      //           attachmentUrl={nextDecoratedMessage.attachmentUrl}
      //           body={nextDecoratedMessage.body}
      //           color={nextDecoratedMessage.color}
      //           contact={nextDecoratedMessage.contact}
      //           contentType={nextDecoratedMessage.contentType}
      //           date={nextDecoratedMessage.date}
      //           isMmsThread={nextDecoratedMessage.isMmsThread}
      //           isSelf={nextDecoratedMessage.isSelf}
      //           isStreak={nextDecoratedMessage.isStreak}
      //           reactions={nextDecoratedMessage.reactions}
      //           threadId={nextDecoratedMessage.threadId}
      //         />
      //       )
      //       index++;
      //     } else {
      //       console.log('out of streak')
      //       inStreak = false;
      //     }
      //   }

      //   return (
      //     <Fade top duration={500}>
      //       <div key={i}>
      //         {messagesStreak}
      //       </div>
      //     </Fade>
      //   )
      // }

      return (
        <Fade top duration={500}>
          <Message
            key={i}
            attachmentUrl={m.attachmentUrl}
            body={m.body}
            color={m.color}
            contact={m.contact}
            contentType={m.contentType}
            date={m.date}
            isMmsThread={m.isMmsThread}
            isSelf={m.isSelf}
            isStreak={m.isStreak}
            reactions={m.reactions}
            threadId={m.threadId}
          />
        </Fade>
      );
    });

    return (
      <div
        className={style.content}
        ref={node => {
          if (node) this._content = node;
        }}
      >
        {decoratedGroupedMessages}
        <div
          style={{ clear: 'both' }}
          ref={node => {
            if (node) this._messagesEnd = node;
          }}
        />
      </div>
    );
  }

  decorateMessage(m: *, index: number) {
    const contact = this.props.contacts[m.sender] || {};
    const isSelf = m.type === 2;

    const defaultColor = getRandomColor();
    const isMmsThread = Object.keys(this.props.contacts).length > 1;

    let isStreak = false;
    const nextMessage = this.props.messages.messages[index + 1] || {};
    if (
      nextMessage.sender === m.sender &&
      differenceInSeconds(nextMessage.date, m.date) < MAX_STREAK_DELAY_S
    ) {
      isStreak = true;
    }

    let color = (contact && contact.color) || defaultColor;
    if (isSelf) {
      color = null;
    }

    const reactions = (m.reactions || []).map(r => {
      const reactorContact = this.props.contacts[r.reactorAddress];
      const reactorName =
        (reactorContact && reactorContact.displayName) ||
        r.reactorAddress.toString();
      return {
        ...r,
        reactorName
      };
    });

    return {
      ...m,
      color,
      contact,
      isMmsThread,
      isSelf,
      isStreak,
      reactions
    };
  }
}
