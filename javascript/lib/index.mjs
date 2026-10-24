// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

// ES module entry point; the implementation lives in index.cjs.
import sdk from './index.cjs';

export const {
  WizardApplication,
  Page,
  VERSION,
  WizardApplicationError,
  ConnectionError,
  BadRequestError,
  AuthenticationError,
  PermissionDeniedError,
  NotFoundError,
  ValidationError,
  RateLimitError,
  ServerError,
} = sdk;
export default WizardApplication;
