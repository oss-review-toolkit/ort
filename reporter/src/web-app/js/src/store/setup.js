import { applyMiddleware, createStore } from 'redux';

import logger from 'redux-logger'
import reducer from './reducers';

const store = createStore(
<<<<<<< HEAD
    reducer,
    applyMiddleware(logger)
=======
  reducer,
  applyMiddleware(logger)
>>>>>>> add redux state management
)

export default store;