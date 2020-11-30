// @flow

import React from 'react';

import type { ElementRef } from 'react';

import style from './app.scss';

type Props = {
  value: string,
  onChange: Function,
  handleSendMessage: Function,
  isEditingMessage: Function
};

export default class AutoSizeInput extends React.Component<Props> {
  _textarea: ElementRef<*>;

  componentDidMount() {
    this._textarea.style.height = this._textarea.scrollHeight + 'px';
  }

  handleChange = (e: Object) => {
    this._textarea.style.height = 0; // has to set to 0 for shrinking
    this._textarea.style.height = this._textarea.scrollHeight + 'px';
    this.props.onChange(e.target.value);
  };

  handleFocus = (e: Object) => {
    this.props.isEditingMessage(true);
  };

  handleBlur = (e: Object) => {
    this.props.isEditingMessage(false);
  };

  handleKeyUp = (e: Object) => {
    if (e.key === 'Escape') {
      e.target.blur();
    }
  };

  render() {
    return (
      <div
        className={style.inputCont}
        onKeyPress={this.props.handleSendMessage}
      >
        <textarea
          className={style.input}
          ref={node => {
            if (node) {
              this._textarea = node;
            }
          }}
          rows="1"
          onChange={this.handleChange}
          value={this.props.value}
          onFocus={this.handleFocus}
          onBlur={this.handleBlur}
          onKeyUp={this.handleKeyUp}
        />
      </div>
    );
  }
}
