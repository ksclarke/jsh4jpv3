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
        CODE_KEY_FOUND=0

        IFS='&' read -r -a PAIRS <<< "$POST_DATA"

        for PAIR in "${PAIRS[@]}"; do
          KEY="${PAIR%%=*}"
          VALUE="${PAIR#*=}"
          KEY=$(echo -e "${KEY//%/\\x}")
          VALUE=$(echo -e "${VALUE//%/\\x}")

          if [ "$KEY" = "code" ]; then
            CODE_KEY_FOUND=1

            echo "${VALUE//+/$' '}" >> $TMP_FILE
            echo "/exit" >> $TMP_FILE

            # We redirect stderr because we want to display those, too
            /usr/bin/env jshell --startup ../imports.jsh -q --class-path ../jpv3.jar $TMP_FILE 2>&1
          fi
        done

        rm $TMP_FILE

        if [ "$CODE_KEY_FOUND" -ne 1 ]; then
          echo "Error: No code field name was found"
        fi
    else
      echo "Error: No code was submitted"
    fi
else
  echo "Error: Code must be submitted through a POST"
fi
