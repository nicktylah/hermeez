// @flow
import React, {Component, PropTypes} from 'react';

export default class Search extends Component {
  static propTypes = {
    search: PropTypes.func,
    searchInputRef: PropTypes.func
  };

  constructor(props) {
    super(props);
  }

  handleKeyPress(e) {
    const target = e.target;
    if (e.key === 'Enter') {
      this.props.search(target.value);
    }
  }

  render() {
    return (
      <div className="search-container">
        <input
          ref={this.props.searchInputRef}
          className="search-input"
          placeholder="Search..."
          onKeyPress={this.handleKeyPress.bind(this)}/>
      </div>
    );
  }

}
