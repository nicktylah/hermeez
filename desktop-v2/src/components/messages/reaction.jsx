// @flow
import React, { Component, PropTypes } from 'react';

import style from './messages.scss';

type Props = {
  isSelf: boolean,
  names: ?(string[]),
  type: string
};

export default class Reaction extends React.Component<Props> {
  render() {
    let { isSelf, names, type } = this.props;

    names = names || [];

    return (
      <div
        style={isSelf ? { left: -10 } : { right: -10 }}
        className={style.reactionCont}
      >
        <div className={style.reaction}>{type}</div>
        <div className={style.names}>{names.join(', ')}</div>
      </div>
    );
  }
}
