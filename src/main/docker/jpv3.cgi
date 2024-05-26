#!/bin/bash

#
# A quick and dirty script to run JShell as a service.
# More error handling and security checking is needed.
#

echo "Content-type: text/plain"
echo "Access-Control-Allow-Origin: *"
echo "Access-Control-Allow-Methods: POST"
echo "Access-Control-Allow-Headers: Content-Type, Authorization"
echo ""

if [ "$REQUEST_METHOD" = "POST" ]; then
    if [ "$CONTENT_LENGTH" -gt 0 ]; then
        read -n $CONTENT_LENGTH POST_DATA <&0

        TMP_FILE=`mktemp`
        CODE_DELIMETER="="

        # Parse the raw form submission
        DELIMITED_DATA="${POST_DATA#*"$CODE_DELIMETER"}"
        ENCODED_DATA="${DELIMITED_DATA//+/ }"
        DECODED_DATA=$(printf '%b' "${ENCODED_DATA//%/\\x}")

        # Write code to a file that we can run through JShell
        echo "$DECODED_DATA" >> $TMP_FILE
        echo "/exit" >> $TMP_FILE

        # We redirect stderr because we want to display those, too
        /usr/bin/env jshell --startup ../imports.jsh -q --class-path ../jpv3.jar $TMP_FILE 2>&1

        # Clean up after ourselves
        rm $TMP_FILE
    else
      echo "Error: No code was submitted"
    fi
else
  echo "Error: Code must be submitted through a POST"
fi
