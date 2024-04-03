import Header from "@cloudscape-design/components/header";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Button from "@cloudscape-design/components/button";
import {ButtonDropdown, ButtonDropdownProps} from "@cloudscape-design/components";


export const PageHeader = (props: {
    title: string;
    buttons?: Array<{
        key?: string,
        text: string,
        disabled?: boolean,
        href?: string,
        items?: ButtonDropdownProps.ItemOrGroup[],
    }>
}) => {
    return (
            <Header
                    variant="h1"
                    actions={!!props.buttons && (
                            <SpaceBetween direction="horizontal" size="xs">
                                {props.buttons.map((button, index) =>
                                        !button.items ? (
                                                <Button key={button.key || index} href={button.href || ''}
                                                        disabled={!!button.disabled}>
                                                    {button.text}
                                                </Button>
                                        ) : (
                                                <ButtonDropdown key={button.key || index} items={button.items}
                                                                disabled={!!button.disabled}>
                                                    {button.text}
                                                </ButtonDropdown>
                                        )
                                )}
                            </SpaceBetween>
                    )}
            >
                {props.title}
            </Header>
    );
};

