### MergePreMaster

#### For all your pre needs


### Usage
#### Command-Line Options
|Option| Description                                                               |
|---|---------------------------------------------------------------------------|
|--dryrun| Git commands are not actually ran |
|--fresh| Target branch is reset to base branch |
|-r, --remote| Git remote to use (default "origin") |
|-b, --base| Base branch that the target branch is based off (default "master") |
|-t, --target| Target branch that the prs will be merged into (default "dev") |
|<prNum>| List of PRs to be merged |

Before any prs are merged, the base branch is merged into the target branch

#### Example Command

(current directory is a GTNH org repo)  
`java -jar MergePreMaster.jar 5 22 69`  
Will merge PR 5, 22, and 69 into the dev branch, creating it off the base branch if it doesnt exist
