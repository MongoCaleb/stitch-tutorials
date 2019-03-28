import { useState, useLayoutEffect } from 'react';

function _slicedToArray(arr, i) {
  return _arrayWithHoles(arr) || _iterableToArrayLimit(arr, i) || _nonIterableRest();
}

function _arrayWithHoles(arr) {
  if (Array.isArray(arr)) return arr;
}

function _iterableToArrayLimit(arr, i) {
  var _arr = [];
  var _n = true;
  var _d = false;
  var _e = undefined;

  try {
    for (var _i = arr[Symbol.iterator](), _s; !(_n = (_s = _i.next()).done); _n = true) {
      _arr.push(_s.value);

      if (i && _arr.length === i) break;
    }
  } catch (err) {
    _d = true;
    _e = err;
  } finally {
    try {
      if (!_n && _i["return"] != null) _i["return"]();
    } finally {
      if (_d) throw _e;
    }
  }

  return _arr;
}

function _nonIterableRest() {
  throw new TypeError("Invalid attempt to destructure non-iterable instance");
}

function useTimeout(a){var b=1<arguments.length&&void 0!==arguments[1]?arguments[1]:0,c=useState(!1),d=_slicedToArray(c,2),e=d[0],f=d[1];return useLayoutEffect(function(){if(e){var c=window.setTimeout(a,b);return function(){window.clearTimeout(c);}}},[e]),{clear:function(){f(!1);},start:function(){f(!0);}}}

export default useTimeout;
//# sourceMappingURL=index.browser.esm.js.map
