// @flow
import { connect } from 'react-redux';

import React, { Component, PropTypes } from 'react';
// import { PhoneNumberFormat, PhoneNumberUtil } from 'google-libphonenumber';

import { searchThreads } from '../../actions/threads';

import style from './search.scss';

import type { ElementRef } from 'react';

type Props = {
  focusSearchBar: boolean
};
type State = {
  value: string
};

// const phoneUtil = PhoneNumberUtil.getInstance();

class Search extends React.Component<Props, State> {
  _input: ElementRef<*>;

  constructor() {
    super();
    this.state = {
      value: ''
    };
  }

  render() {
    return (
      <input
        ref={node => {
          if (node) {
            this._input = node;
            if (this.props.focusSearchBar) {
              this._input.focus();
            } else {
              this._input.blur();
            }
          }
        }}
        className={style.searchInput}
        placeholder="Search threads and contacts..."
      />
    );
  }
}

const mapStateToProps = state => ({
  focusSearchBar: state.app.focusSearchBar
});
export default connect(mapStateToProps, {
  searchThreads
})(Search);
